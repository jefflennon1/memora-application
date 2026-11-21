package br.com.memora.membership.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JoinRequestRepository extends JpaRepository<JoinRequest, UUID> {

    Optional<JoinRequest> findByIdAndEventId(UUID id, UUID eventId);

    Page<JoinRequest> findByEventIdAndStatusOrderByRequestedAtAscIdAsc(UUID eventId, JoinRequestStatus status,
                                                                        Pageable pageable);

    /** Pedido mais recente da pessoa para o evento (pendente, aprovado ou rejeitado). */
    Optional<JoinRequest> findFirstByEventIdAndUserIdOrderByRequestedAtDesc(UUID eventId, UUID userId);
}
