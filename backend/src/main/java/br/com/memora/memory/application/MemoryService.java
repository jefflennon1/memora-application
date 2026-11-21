package br.com.memora.memory.application;

import br.com.memora.event.application.EventAccess;
import br.com.memora.event.application.EventAccess.EventContext;
import br.com.memora.membership.application.PeopleDirectory;
import br.com.memora.membership.application.PeopleDirectory.PersonView;
import br.com.memora.memory.application.MediaService.MediaView;
import br.com.memora.memory.application.MemoryEngagement.Engagement;
import br.com.memora.memory.domain.MediaFile;
import br.com.memora.memory.domain.MediaFileRepository;
import br.com.memora.memory.domain.Memory;
import br.com.memora.memory.domain.MemoryRepository;
import br.com.memora.memory.domain.MemoryVisibility;
import br.com.memora.memory.domain.MediaKind;
import br.com.memora.notification.EventStreamService.MemoryCreated;
import br.com.memora.shared.error.BusinessRuleException;
import br.com.memora.shared.error.ForbiddenException;
import br.com.memora.shared.error.ValidationException;
import br.com.memora.shared.storage.AfterCommitStorageCleanup;
import br.com.memora.shared.web.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

/**
 * Memórias do mural.
 *
 * <ul>
 *   <li>Publicar: participante, com o mural aberto. Texto (até 500 caracteres) e/ou até 10 mídias já enviadas.</li>
 *   <li>Visibilidade padrão {@code PARTICIPANTS_ONLY}. {@code PUBLIC} só em evento público: memórias criadas
 *       enquanto o evento era privado continuam restritas até o autor decidir torná-las públicas.</li>
 *   <li>Editar: só o autor, participante, com o mural aberto. A data de publicação não muda.</li>
 *   <li>Excluir: só o autor (mesmo com o mural fechado). As imagens, os comentários e as reações são apagados
 *       junto.</li>
 * </ul>
 */
@Service
public class MemoryService {

    private final MemoryRepository memories;
    private final MediaFileRepository media;
    private final EventAccess events;
    private final MemoryAccess access;
    private final PeopleDirectory people;
    private final MemoryEngagement engagement;
    private final AfterCommitStorageCleanup cleanup;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public MemoryService(MemoryRepository memories, MediaFileRepository media, EventAccess events,
                         MemoryAccess access, PeopleDirectory people, MemoryEngagement engagement,
                         AfterCommitStorageCleanup cleanup, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.memories = memories;
        this.media = media;
        this.events = events;
        this.access = access;
        this.people = people;
        this.engagement = engagement;
        this.cleanup = cleanup;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public MemoryView publish(UUID eventId, UUID authorId, PublishCommand command) {
        EventContext context = events.requireVisible(eventId, authorId);
        context.requireParticipant();
        context.requireMuralOpen();
        MemoryVisibility visibility = resolveVisibility(command.visibility(), MemoryVisibility.PARTICIPANTS_ONLY,
                context);
        List<UUID> mediaIds = distinct(command.mediaIds());
        Instant now = clock.instant();

        Memory memory = Memory.publish(eventId, authorId, command.text(), mediaIds.size(), visibility, now);
        List<MediaFile> files = loadAttachable(mediaIds, authorId, eventId, null);
        memories.save(memory);
        for (int i = 0; i < files.size(); i++) {
            files.get(i).attachToMemory(memory.getId(), i, now);
        }
        memories.flush();
        eventPublisher.publishEvent(new MemoryCreated(eventId, memory.getId(), authorId, now));
        return toView(memory, files, people.summary(authorId, authorId), Engagement.NONE, authorId);
    }

    /** Mural: todas as memórias para participantes; só as públicas para os demais. */
    @Transactional(readOnly = true)
    public Page<MemoryView> mural(UUID eventId, UUID viewerId, MuralOrder order, int page, int size) {
        EventContext context = events.requireVisible(eventId, viewerId);
        Sort sort = order == MuralOrder.NEWEST
                ? Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
                : Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
        Pageable requested = PageResponse.request(page, size);
        Pageable pageable = PageRequest.of(requested.getPageNumber(), requested.getPageSize(), sort);
        Page<Memory> result = context.isParticipant()
                ? memories.findByEventId(eventId, pageable)
                : memories.findByEventIdAndVisibility(eventId, MemoryVisibility.PUBLIC, pageable);
        return toViews(result, viewerId, context.isParticipant());
    }

    @Transactional(readOnly = true)
    public MemoryView get(UUID memoryId, UUID viewerId) {
        MemoryAccess.Visible visible = access.requireVisible(memoryId, viewerId);
        return toView(visible.memory(), media.findByMemoryIdOrderByPositionAsc(memoryId), visible.event(), viewerId);
    }

    /**
     * Edição pelo autor. {@code text}: {@code null} mantém, {@code Optional.empty()} apaga. {@code mediaIds}:
     * {@code null} mantém; uma lista substitui as imagens (na ordem dada), e as que saírem são apagadas.
     */
    @Transactional
    public MemoryView update(UUID memoryId, UUID userId, UpdateCommand command) {
        MemoryAccess.Visible visible = access.requireVisible(memoryId, userId);
        Memory memory = visible.memory();
        requireAuthor(memory, userId);
        visible.event().requireParticipant();
        visible.event().requireMuralOpen();

        List<MediaFile> current = media.findByMemoryIdOrderByPositionAsc(memoryId);
        List<UUID> newIds = command.mediaIds() == null
                ? current.stream().map(MediaFile::getId).toList()
                : distinct(command.mediaIds());
        String text = command.text() == null ? memory.getText() : command.text().orElse(null);
        MemoryVisibility visibility = resolveVisibility(command.visibility(), memory.getVisibility(),
                visible.event());
        Instant now = clock.instant();
        boolean restricted = memory.getVisibility() == MemoryVisibility.PUBLIC
                && visibility == MemoryVisibility.PARTICIPANTS_ONLY;

        memory.edit(text, newIds.size(), visibility, now);
        List<MediaFile> files = loadAttachable(newIds, userId, memory.getEventId(), memoryId);
        Set<UUID> kept = new HashSet<>(newIds);
        List<MediaFile> removed = current.stream().filter(f -> !kept.contains(f.getId())).toList();
        media.deleteAll(removed);
        for (int i = 0; i < files.size(); i++) {
            files.get(i).attachToMemory(memoryId, i, now);
        }
        memories.flush();
        if (restricted) {
            engagement.memoryRestricted(memoryId);
        }
        cleanup.deleteAfterCommit(removed.stream().map(MediaFile::getStorageKey).toList());
        return toView(memory, files, visible.event(), userId);
    }

    /**
     * Exclusão pelo autor, também com o mural fechado. Imagens são apagadas junto; comentários e reações saem pelo
     * banco ({@code ON DELETE CASCADE}).
     */
    @Transactional
    public void delete(UUID memoryId, UUID userId) {
        Memory memory = access.requireVisible(memoryId, userId).memory();
        requireAuthor(memory, userId);
        List<MediaFile> files = new java.util.ArrayList<>(media.findByMemoryIdOrderByPositionAsc(memoryId));
        files.addAll(media.findCommentMediaByMemoryId(memoryId));
        media.deleteAll(files);
        memories.delete(memory);
        memories.flush();
        cleanup.deleteAfterCommit(files.stream().map(MediaFile::getStorageKey).toList());
    }

    // ------------------------------------------------------------------ regras

    private static void requireAuthor(Memory memory, UUID userId) {
        if (!memory.isAuthoredBy(userId)) {
            throw new ForbiddenException("MEMORY_AUTHOR_ONLY", "Só quem publicou a memória pode fazer isso.");
        }
    }

    private static MemoryVisibility resolveVisibility(MemoryVisibility requested, MemoryVisibility fallback,
                                                      EventContext context) {
        MemoryVisibility visibility = requested == null ? fallback : requested;
        if (requested == MemoryVisibility.PUBLIC && !context.isPublic()) {
            throw new BusinessRuleException("MEMORY_PUBLIC_REQUIRES_PUBLIC_EVENT",
                    "Em um evento privado, as memórias são sempre só para participantes.");
        }
        return visibility;
    }

    private static List<UUID> distinct(List<UUID> ids) {
        if (ids == null) {
            return List.of();
        }
        if (ids.stream().anyMatch(java.util.Objects::isNull)) {
            throw new ValidationException("INVALID_MEDIA", "Lista de mídias inválida.");
        }
        Set<UUID> unique = new LinkedHashSet<>(ids);
        if (unique.size() != ids.size()) {
            throw new ValidationException("DUPLICATE_MEDIA", "A mesma mídia foi informada mais de uma vez.");
        }
        return List.copyOf(unique);
    }

    /**
     * Cada imagem precisa ser: já desta memória ({@code memoryId}), ou enviada por esta pessoa, neste evento, e
     * ainda não publicada. Qualquer outra (de outra pessoa, de outro evento, de outra memória, inexistente)
     * recusa a operação inteira com {@code 400 INVALID_MEDIA}, sem revelar o motivo.
     */
    private List<MediaFile> loadAttachable(List<UUID> ids, UUID userId, UUID eventId, UUID memoryId) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, MediaFile> found = media.findAllById(ids).stream()
                .collect(Collectors.toMap(MediaFile::getId, Function.identity()));
        List<MediaFile> files = ids.stream().map(id -> {
            MediaFile file = found.get(id);
            boolean ok = file != null && (file.isPendingFor(userId, eventId)
                    || (memoryId != null && memoryId.equals(file.getMemoryId())));
            if (!ok) {
                throw new ValidationException("INVALID_MEDIA",
                        "Uma das mídias não existe ou não pode ser usada nesta memória. Envie-a novamente.");
            }
            return file;
        }).toList();
        if (files.stream().filter(f -> f.getKind() == MediaKind.AUDIO).count() > 1) {
            throw new BusinessRuleException("TOO_MANY_AUDIO", "Uma memória pode ter no máximo um áudio.");
        }
        if (files.stream().filter(f -> f.getKind() == MediaKind.VIDEO).count() > 1) {
            throw new BusinessRuleException("TOO_MANY_VIDEO", "Uma memória pode ter no máximo um vídeo.");
        }
        return files;
    }

    // ------------------------------------------------------------------ visões

    private Page<MemoryView> toViews(Page<Memory> page, UUID viewerId, boolean viewerIsParticipant) {
        List<UUID> ids = page.getContent().stream().map(Memory::getId).toList();
        Map<UUID, List<MediaFile>> filesByMemory = ids.isEmpty() ? Map.of()
                : media.findByMemoryIdInOrderByMemoryIdAscPositionAsc(ids).stream()
                        .collect(Collectors.groupingBy(MediaFile::getMemoryId));
        Map<UUID, PersonView> authors = people.summaries(viewerId,
                page.getContent().stream().map(Memory::getAuthorId).toList());
        Map<UUID, Engagement> engagements = ids.isEmpty() ? Map.of()
                : engagement.of(ids, viewerId, viewerIsParticipant);
        return page.map(m -> toView(m, filesByMemory.getOrDefault(m.getId(), List.of()),
                authors.get(m.getAuthorId()), engagements.getOrDefault(m.getId(), Engagement.NONE), viewerId));
    }

    private MemoryView toView(Memory memory, List<MediaFile> files, EventContext context, UUID viewerId) {
        Engagement counts = engagement.of(List.of(memory.getId()), viewerId, context.isParticipant())
                .getOrDefault(memory.getId(), Engagement.NONE);
        return toView(memory, files, people.summary(viewerId, memory.getAuthorId()), counts, viewerId);
    }

    private static MemoryView toView(Memory m, List<MediaFile> files, PersonView author, Engagement engagement,
                                     UUID viewerId) {
        return new MemoryView(m.getId(), m.getEventId(), author, m.getText(), m.getVisibility(),
                files.stream().map(MediaView::of).toList(), m.getCreatedAt(), m.getEditedAt(),
                viewerId != null && m.isAuthoredBy(viewerId), engagement);
    }

    // ------------------------------------------------------------------ contratos

    public enum MuralOrder {
        /** Ordem cronológica de publicação (padrão). */
        OLDEST,
        NEWEST
    }

    public record PublishCommand(String text, List<UUID> mediaIds, MemoryVisibility visibility) {
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public record UpdateCommand(Optional<String> text, MemoryVisibility visibility, List<UUID> mediaIds) {
    }

    public record MemoryView(UUID id, UUID eventId, PersonView author, String text, MemoryVisibility visibility,
                             List<MediaView> media, Instant createdAt, Instant editedAt, boolean mine,
                             Engagement engagement) {
    }
}
