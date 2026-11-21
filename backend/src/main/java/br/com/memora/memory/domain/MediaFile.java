package br.com.memora.memory.domain;

import br.com.memora.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Metadados de um arquivo de mídia. O conteúdo fica no armazenamento de objetos, na chave {@link #getStorageKey()},
 * que não contém nada sobre a pessoa nem o nome original do arquivo.
 *
 * <p>Nasce pendente (sem memória) e é vinculado ao publicar ou editar uma memória.
 */
@Entity
@Table(name = "media_file")
public class MediaFile extends BaseEntity {

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "uploader_id", nullable = false, updatable = false)
    private UUID uploaderId;

    @Column(name = "memory_id")
    private UUID memoryId;

    @Column(name = "comment_id")
    private UUID commentId;

    @Column(name = "position")
    private Short position;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 20, nullable = false, updatable = false)
    private MediaKind kind;

    @Column(name = "content_type", length = 50, nullable = false, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "width", updatable = false)
    private Integer width;

    @Column(name = "height", updatable = false)
    private Integer height;

    @Column(name = "duration_ms", updatable = false)
    private Integer durationMs;

    @Column(name = "storage_key", length = 200, nullable = false, updatable = false)
    private String storageKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "attached_at")
    private Instant attachedAt;

    protected MediaFile() {
        // JPA
    }

    public static MediaFile pendingImage(UUID eventId, UUID uploaderId, ImageInspector.ImageInfo info, long sizeBytes,
                                         Instant now) {
        MediaFile media = new MediaFile();
        media.eventId = Objects.requireNonNull(eventId);
        media.uploaderId = Objects.requireNonNull(uploaderId);
        media.kind = MediaKind.IMAGE;
        media.contentType = info.contentType();
        media.width = info.width();
        media.height = info.height();
        media.sizeBytes = sizeBytes;
        media.storageKey = "events/" + eventId + "/media/" + media.getId();
        media.createdAt = now;
        return media;
    }

    public static MediaFile pendingAudio(UUID eventId, UUID uploaderId, AudioInspector.AudioInfo info,
                                         long sizeBytes, Instant now) {
        MediaFile media = new MediaFile();
        media.eventId = Objects.requireNonNull(eventId);
        media.uploaderId = Objects.requireNonNull(uploaderId);
        media.kind = MediaKind.AUDIO;
        media.contentType = info.contentType();
        media.durationMs = Math.toIntExact(info.durationMs());
        media.sizeBytes = sizeBytes;
        media.storageKey = "events/" + eventId + "/media/" + media.getId();
        media.createdAt = now;
        return media;
    }

    public static MediaFile pendingVideo(UUID eventId, UUID uploaderId, VideoInspector.VideoInfo info,
                                         long sizeBytes, Instant now) {
        MediaFile media = new MediaFile();
        media.eventId = Objects.requireNonNull(eventId);
        media.uploaderId = Objects.requireNonNull(uploaderId);
        media.kind = MediaKind.VIDEO;
        media.contentType = info.contentType();
        media.width = info.width();
        media.height = info.height();
        media.durationMs = Math.toIntExact(info.durationMs());
        media.sizeBytes = sizeBytes;
        media.storageKey = "events/" + eventId + "/media/" + media.getId();
        media.createdAt = now;
        return media;
    }

    public boolean isPending() {
        return memoryId == null && commentId == null;
    }

    /** Pode ser vinculada a uma memória desta pessoa neste evento. */
    public boolean isPendingFor(UUID userId, UUID eventId) {
        return isPending() && uploaderId.equals(userId) && this.eventId.equals(eventId);
    }

    public void attachToMemory(UUID memoryId, int position, Instant now) {
        if (this.memoryId == null) {
            this.attachedAt = now;
        }
        this.memoryId = Objects.requireNonNull(memoryId);
        this.commentId = null;
        this.position = (short) position;
    }

    public void attachToComment(UUID commentId, Instant now) {
        if (kind != MediaKind.AUDIO) {
            throw new IllegalStateException("Só áudio pode ser anexado a comentário.");
        }
        this.commentId = Objects.requireNonNull(commentId);
        this.memoryId = null;
        this.position = null;
        this.attachedAt = now;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getUploaderId() {
        return uploaderId;
    }

    public UUID getMemoryId() {
        return memoryId;
    }

    public UUID getCommentId() {
        return commentId;
    }

    public int getPosition() {
        return position == null ? -1 : position;
    }

    public String getContentType() {
        return contentType;
    }

    public MediaKind getKind() {
        return kind;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Integer getWidth() {
        return width;
    }

    public Integer getHeight() {
        return height;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
