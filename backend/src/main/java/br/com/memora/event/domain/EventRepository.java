package br.com.memora.event.domain;

import br.com.memora.shared.text.SearchText;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    /** Eventos dos quais a pessoa participa, com qualquer papel. */
    @Query(value = """
            select e from Event e
            where e.id in (select p.eventId from EventParticipant p where p.userId = :userId)""",
            countQuery = """
            select count(e) from Event e
            where e.id in (select p.eventId from EventParticipant p where p.userId = :userId)""")
    Page<Event> findParticipating(@Param("userId") UUID userId, Pageable pageable);

    /** Filtros da busca pública. Eventos privados nunca entram, independentemente dos demais filtros. */
    final class PublicSearch {

        private PublicSearch() {
        }

        /**
         * @param name     trecho do nome (sem diferenciar maiúsculas nem acentos), opcional
         * @param city     trecho da cidade (idem), opcional
         * @param category código da categoria, opcional
         */
        public static Specification<Event> of(String name, String city, String category) {
            Specification<Event> spec = (root, query, cb) -> cb.equal(root.get("privacy"), EventPrivacy.PUBLIC);
            String nameTerm = SearchText.normalize(name);
            if (nameTerm != null) {
                spec = spec.and((root, query, cb) ->
                        cb.like(root.get("nameSearch"), SearchText.containsPattern(nameTerm), '\\'));
            }
            String cityTerm = SearchText.normalize(city);
            if (cityTerm != null) {
                spec = spec.and((root, query, cb) ->
                        cb.like(root.get("citySearch"), SearchText.containsPattern(cityTerm), '\\'));
            }
            if (category != null && !category.isBlank()) {
                String code = category.strip();
                spec = spec.and((root, query, cb) -> cb.equal(root.get("categoryCode"), code));
            }
            return spec;
        }
    }
}
