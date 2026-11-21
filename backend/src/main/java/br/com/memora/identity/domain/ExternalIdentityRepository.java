package br.com.memora.identity.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentity, UUID> {

    @Query("select i from ExternalIdentity i join fetch i.user "
            + "where i.provider = :provider and i.providerSubject = :subject")
    Optional<ExternalIdentity> findByProviderSubject(IdentityProvider provider, String subject);

    List<ExternalIdentity> findByUserIdOrderByLinkedAtAsc(UUID userId);
}
