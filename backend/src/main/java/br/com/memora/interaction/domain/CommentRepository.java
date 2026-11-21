package br.com.memora.interaction.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /** Comentários principais de uma memória (participantes). A ordem vem do {@link Pageable}. */
    Page<Comment> findByMemoryIdAndParentIdIsNull(UUID memoryId, Pageable pageable);

    /** Comentários principais visíveis a quem não participa. */
    Page<Comment> findByMemoryIdAndParentIdIsNullAndVisibility(UUID memoryId, CommentVisibility visibility,
                                                               Pageable pageable);

    Page<Comment> findByParentId(UUID parentId, Pageable pageable);

    Page<Comment> findByParentIdAndVisibility(UUID parentId, CommentVisibility visibility, Pageable pageable);

    @Query("select c.id from Comment c where c.id = :id or c.parentId = :id")
    List<UUID> findTreeIds(@Param("id") UUID id);

    /** Comentários e respostas por memória. */
    @Query("select c.memoryId as id, count(c) as total from Comment c where c.memoryId in :memoryIds "
            + "group by c.memoryId")
    List<IdCount> countByMemory(@Param("memoryIds") Collection<UUID> memoryIds);

    @Query("select c.memoryId as id, count(c) as total from Comment c where c.memoryId in :memoryIds "
            + "and c.visibility = :visibility group by c.memoryId")
    List<IdCount> countByMemory(@Param("memoryIds") Collection<UUID> memoryIds,
                                @Param("visibility") CommentVisibility visibility);

    /** Respostas por comentário principal. */
    @Query("select c.parentId as id, count(c) as total from Comment c where c.parentId in :parentIds "
            + "group by c.parentId")
    List<IdCount> countReplies(@Param("parentIds") Collection<UUID> parentIds);

    @Query("select c.parentId as id, count(c) as total from Comment c where c.parentId in :parentIds "
            + "and c.visibility = :visibility group by c.parentId")
    List<IdCount> countReplies(@Param("parentIds") Collection<UUID> parentIds,
                               @Param("visibility") CommentVisibility visibility);

    /** Todos os comentários e respostas da memória passam a ser só para participantes. */
    @Modifying(flushAutomatically = true)
    @Query("update Comment c set c.visibility = br.com.memora.interaction.domain.CommentVisibility.PARTICIPANTS_ONLY "
            + "where c.memoryId = :memoryId and c.visibility = br.com.memora.interaction.domain.CommentVisibility.PUBLIC")
    int restrictAllOfMemory(@Param("memoryId") UUID memoryId);

    /** As respostas do comentário passam a ser só para participantes. */
    @Modifying(flushAutomatically = true)
    @Query("update Comment c set c.visibility = br.com.memora.interaction.domain.CommentVisibility.PARTICIPANTS_ONLY "
            + "where c.parentId = :parentId and c.visibility = br.com.memora.interaction.domain.CommentVisibility.PUBLIC")
    int restrictReplies(@Param("parentId") UUID parentId);

    interface IdCount {
        UUID getId();

        Long getTotal();
    }
}
