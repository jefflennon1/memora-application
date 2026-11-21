package br.com.memora.memory.domain;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryRepository extends JpaRepository<Memory, UUID> {

    /** Mural completo (participantes). A ordem vem do {@link Pageable}. */
    Page<Memory> findByEventId(UUID eventId, Pageable pageable);

    /** Mural de quem não participa: só memórias públicas. */
    Page<Memory> findByEventIdAndVisibility(UUID eventId, MemoryVisibility visibility, Pageable pageable);
}
