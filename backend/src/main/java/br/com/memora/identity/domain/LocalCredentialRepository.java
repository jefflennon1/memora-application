package br.com.memora.identity.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalCredentialRepository extends JpaRepository<LocalCredential, UUID> {

    Optional<LocalCredential> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
