package br.com.memora.membership.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventInviteRepository extends JpaRepository<EventInvite, UUID> {

    Optional<EventInvite> findByToken(String token);

    Optional<EventInvite> findByIdAndEventId(UUID id, UUID eventId);

    Page<EventInvite> findByEventIdOrderByCreatedAtDesc(UUID eventId, Pageable pageable);

    /** Convites nominais ainda válidos para a pessoa. */
    @Query("""
            select i from EventInvite i
            where i.inviteeUserId = :userId and i.revokedAt is null and i.usedAt is null and i.expiresAt > :now
            order by i.createdAt desc""")
    List<EventInvite> findActiveNamedFor(@Param("userId") UUID userId, @Param("now") Instant now);
}
