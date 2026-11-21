package br.com.memora.memory.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaFileRepository extends JpaRepository<MediaFile, UUID> {

    List<MediaFile> findByMemoryIdOrderByPositionAsc(UUID memoryId);

    List<MediaFile> findByMemoryIdInOrderByMemoryIdAscPositionAsc(Collection<UUID> memoryIds);

    List<MediaFile> findByCommentIdIn(Collection<UUID> commentIds);

    java.util.Optional<MediaFile> findByCommentId(UUID commentId);

    @Query(value = "select m.* from media_file m join comment c on c.id = m.comment_id "
            + "where c.memory_id = :memoryId", nativeQuery = true)
    List<MediaFile> findCommentMediaByMemoryId(@Param("memoryId") UUID memoryId);

    long countByUploaderIdAndEventIdAndMemoryIdIsNullAndCommentIdIsNull(UUID uploaderId, UUID eventId);

    /** Imagens enviadas e nunca publicadas, mais antigas que o limite. */
    @Query("select m from MediaFile m where m.memoryId is null and m.commentId is null "
            + "and m.createdAt < :before order by m.createdAt")
    List<MediaFile> findPendingCreatedBefore(@Param("before") Instant before, Pageable pageable);
}
