package br.com.memora.membership.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteResponseRepository extends JpaRepository<InviteResponse, UUID> {

    Optional<InviteResponse> findByInviteIdAndUserId(UUID inviteId, UUID userId);

    void deleteByInviteIdAndUserId(UUID inviteId, UUID userId);
}
