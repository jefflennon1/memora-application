package br.com.memora.event.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventCategoryRepository extends JpaRepository<EventCategory, String> {

    List<EventCategory> findByActiveTrueOrderByDisplayOrderAsc();
}
