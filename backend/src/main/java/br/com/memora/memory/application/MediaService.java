package br.com.memora.memory.application;

import br.com.memora.event.application.EventAccess;
import br.com.memora.event.application.EventAccess.EventContext;
import br.com.memora.memory.domain.ImageInspector;
import br.com.memora.memory.domain.AudioInspector;
import br.com.memora.memory.domain.MediaFile;
import br.com.memora.memory.domain.MediaFileRepository;
import br.com.memora.memory.domain.MediaKind;
import br.com.memora.memory.domain.VideoInspector;
import br.com.memora.shared.error.BusinessRuleException;
import br.com.memora.shared.error.NotFoundException;
import br.com.memora.shared.error.PayloadTooLargeException;
import br.com.memora.shared.error.ValidationException;
import br.com.memora.shared.storage.AfterCommitStorageCleanup;
import br.com.memora.shared.storage.ObjectStorage;
import br.com.memora.shared.storage.ObjectStorage.StoredObject;
import br.com.memora.shared.storage.StoredObjectNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envio e entrega de mídia.
 *
 * <p>Envio: só participantes, com o mural aberto. O tipo real é identificado pelo conteúdo
 * ({@link ImageInspector}, {@link AudioInspector} e {@link VideoInspector}). O arquivo fica pendente até ser
 * vinculado a uma memória ou, no caso de áudio, a um comentário.
 *
 * <p>Entrega: sempre pela API. Mídia pendente: só quem enviou. Mídia publicada: quem pode ver a memória.
 * Qualquer outro caso responde como inexistente ({@code 404 MEDIA_NOT_FOUND}).
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final int CLEANUP_BATCH = 200;

    private final MediaFileRepository media;
    private final EventAccess events;
    private final MemoryAccess memories;
    private final ObjectStorage storage;
    private final AfterCommitStorageCleanup cleanup;
    private final MediaProperties properties;
    private final Clock clock;

    public MediaService(MediaFileRepository media, EventAccess events, MemoryAccess memories, ObjectStorage storage,
                        AfterCommitStorageCleanup cleanup, MediaProperties properties, Clock clock) {
        this.media = media;
        this.events = events;
        this.memories = memories;
        this.storage = storage;
        this.cleanup = cleanup;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public MediaView upload(UUID eventId, UUID userId, byte[] content) {
        EventContext context = events.requireVisible(eventId, userId);
        context.requireParticipant();
        context.requireMuralOpen();
        if (content == null || content.length == 0) {
            throw new ValidationException("EMPTY_FILE", "O arquivo enviado está vazio.");
        }
        long maximumBytes = Math.max(properties.maxVideoBytes(),
                Math.max(properties.maxImageBytes(), properties.maxAudioBytes()));
        if (content.length > maximumBytes) {
            throw new PayloadTooLargeException("MEDIA_TOO_LARGE",
                    "O arquivo enviado é maior que o permitido.");
        }
        if (media.countByUploaderIdAndEventIdAndMemoryIdIsNullAndCommentIdIsNull(userId, eventId)
                >= MediaProperties.MAX_PENDING_PER_EVENT) {
            throw new BusinessRuleException("TOO_MANY_PENDING_MEDIA",
                    "Você tem muitas mídias enviadas e ainda não publicadas neste evento. Publique ou descarte "
                            + "algumas antes de enviar outras.");
        }
        Instant now = clock.instant();
        MediaFile file;
        if (AudioInspector.looksLikeAudio(content)) {
            if (content.length > properties.maxAudioBytes()) {
                throw new PayloadTooLargeException("MEDIA_TOO_LARGE", "O áudio pode ter no máximo "
                        + properties.maxAudioBytes() / (1024 * 1024) + " MB.");
            }
            file = MediaFile.pendingAudio(eventId, userId, AudioInspector.inspect(content), content.length, now);
        } else if (VideoInspector.looksLikeVideo(content)) {
            if (content.length > properties.maxVideoBytes()) {
                throw new PayloadTooLargeException("MEDIA_TOO_LARGE", "O vídeo pode ter no máximo "
                        + properties.maxVideoBytes() / (1024 * 1024) + " MB.");
            }
            file = MediaFile.pendingVideo(eventId, userId, VideoInspector.inspect(content), content.length, now);
        } else {
            if (content.length > properties.maxImageBytes()) {
                throw new PayloadTooLargeException("MEDIA_TOO_LARGE", "A imagem pode ter no máximo "
                        + properties.maxImageBytes() / (1024 * 1024) + " MB.");
            }
            file = MediaFile.pendingImage(eventId, userId, ImageInspector.inspect(content), content.length, now);
        }
        media.saveAndFlush(file);
        // Se o envio ao armazenamento falhar, a transação é desfeita e nada fica registrado.
        storage.put(file.getStorageKey(), content, file.getContentType());
        return MediaView.of(file);
    }

    /** Conteúdo da mídia, conferindo quem pode vê-la. O chamador fecha o stream. */
    @Transactional(readOnly = true)
    public MediaContent open(UUID mediaId, UUID viewerId, String rangeHeader) {
        MediaFile file = media.findById(mediaId).orElseThrow(MediaService::notFound);
        boolean allowed = file.isPending()
                ? file.getUploaderId().equals(viewerId)
                : file.getMemoryId() != null && memories.findVisible(file.getMemoryId(), viewerId).isPresent();
        if (!allowed) {
            throw notFound();
        }
        try {
            return stored(file, rangeHeader);
        } catch (StoredObjectNotFoundException e) {
            log.warn("Mídia {} registrada, mas ausente no armazenamento ({})", file.getId(), file.getStorageKey());
            throw notFound();
        }
    }

    /** Abre o áudio depois que o serviço de comentários já validou a visibilidade do comentário. */
    @Transactional(readOnly = true)
    public MediaContent openCommentAudio(UUID mediaId, UUID commentId, String rangeHeader) {
        MediaFile file = media.findById(mediaId)
                .filter(f -> commentId.equals(f.getCommentId()))
                .orElseThrow(MediaService::notFound);
        return stored(file, rangeHeader);
    }

    private MediaContent stored(MediaFile file, String rangeHeader) {
        try {
            long total = storage.length(file.getStorageKey());
            ByteRange range = ByteRange.parse(rangeHeader, total);
            StoredObject object = range.partial()
                    ? storage.get(file.getStorageKey(), range.start(), range.end())
                    : storage.get(file.getStorageKey());
            return new MediaContent(file.getId(), file.getContentType(), object, range.start(), range.end(), total,
                    range.partial());
        } catch (StoredObjectNotFoundException e) {
            log.warn("Mídia {} registrada, mas ausente no armazenamento ({})", file.getId(), file.getStorageKey());
            throw notFound();
        }
    }

    /** Descarta uma mídia enviada e ainda não publicada. */
    @Transactional
    public void discardPending(UUID mediaId, UUID userId) {
        MediaFile file = media.findById(mediaId)
                .filter(f -> f.getUploaderId().equals(userId))
                .orElseThrow(MediaService::notFound);
        if (!file.isPending()) {
            throw new BusinessRuleException("MEDIA_ALREADY_PUBLISHED",
                    "Esta mídia já foi publicada. Para removê-la, edite o conteúdo.");
        }
        media.delete(file);
        cleanup.deleteAfterCommit(List.of(file.getStorageKey()));
    }

    /** Vincula um áudio pendente ao comentário. */
    @Transactional
    public MediaView attachToComment(UUID mediaId, UUID commentId, UUID userId, UUID eventId) {
        MediaFile file = media.findById(mediaId)
                .filter(f -> f.getKind() == MediaKind.AUDIO && f.isPendingFor(userId, eventId))
                .orElseThrow(() -> new ValidationException("INVALID_MEDIA",
                        "O áudio não existe ou não pode ser usado neste comentário."));
        file.attachToComment(commentId, clock.instant());
        media.flush();
        return MediaView.forComment(file, commentId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, MediaView> audiosForComments(Collection<UUID> commentIds) {
        if (commentIds.isEmpty()) return Map.of();
        return media.findByCommentIdIn(commentIds).stream().collect(Collectors.toMap(MediaFile::getCommentId,
                file -> MediaView.forComment(file, file.getCommentId())));
    }

    @Transactional
    public void removeCommentAudios(Collection<UUID> commentIds) {
        if (commentIds.isEmpty()) return;
        List<MediaFile> files = media.findByCommentIdIn(commentIds);
        media.deleteAll(files);
        media.flush();
        cleanup.deleteAfterCommit(files.stream().map(MediaFile::getStorageKey).toList());
    }

    /** Apaga imagens enviadas e nunca publicadas além do prazo de retenção. Devolve quantas foram apagadas. */
    @Transactional
    public int purgeOrphans() {
        List<MediaFile> orphans = media.findPendingCreatedBefore(
                clock.instant().minus(properties.orphanRetention()), PageRequest.of(0, CLEANUP_BATCH));
        media.deleteAll(orphans);
        cleanup.deleteAfterCommit(orphans.stream().map(MediaFile::getStorageKey).toList());
        return orphans.size();
    }

    static NotFoundException notFound() {
        return new NotFoundException("MEDIA_NOT_FOUND", "Arquivo não encontrado.");
    }

    public record MediaView(UUID id, String url, MediaKind kind, String contentType, Integer width, Integer height,
                            Integer durationMs, long sizeBytes) {

        public static MediaView of(MediaFile file) {
            return new MediaView(file.getId(), "/api/v1/media/" + file.getId(), file.getKind(), file.getContentType(),
                    file.getWidth(), file.getHeight(), file.getDurationMs(), file.getSizeBytes());
        }

        public static MediaView forComment(MediaFile file, UUID commentId) {
            return new MediaView(file.getId(), "/api/v1/comments/" + commentId + "/audio", file.getKind(),
                    file.getContentType(), file.getWidth(), file.getHeight(), file.getDurationMs(),
                    file.getSizeBytes());
        }
    }

    public record MediaContent(UUID id, String contentType, StoredObject object, long start, long end,
                               long totalLength, boolean partial) {
    }

    private record ByteRange(long start, long end, boolean partial) {
        static ByteRange parse(String header, long total) {
            if (header == null || !header.startsWith("bytes=") || header.contains(",") || total <= 0) {
                return new ByteRange(0, Math.max(0, total - 1), false);
            }
            try {
                String value = header.substring(6).trim();
                int dash = value.indexOf('-');
                if (dash < 0) return new ByteRange(0, total - 1, false);
                String left = value.substring(0, dash).trim();
                String right = value.substring(dash + 1).trim();
                long start;
                long end;
                if (left.isEmpty()) {
                    long suffix = Long.parseLong(right);
                    if (suffix <= 0) return new ByteRange(0, total - 1, false);
                    start = Math.max(0, total - suffix);
                    end = total - 1;
                } else {
                    start = Long.parseLong(left);
                    end = right.isEmpty() ? total - 1 : Math.min(Long.parseLong(right), total - 1);
                }
                if (start < 0 || start >= total || end < start) return new ByteRange(0, total - 1, false);
                return new ByteRange(start, end, true);
            } catch (NumberFormatException ignored) {
                return new ByteRange(0, total - 1, false);
            }
        }
    }
}
