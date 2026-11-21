package br.com.memora.event.application;

import br.com.memora.event.domain.EventCategory;
import br.com.memora.event.domain.EventCategoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventCategoryService {

    private final EventCategoryRepository repository;

    public EventCategoryService(EventCategoryRepository repository) {
        this.repository = repository;
    }

    /** Categorias disponíveis para classificar eventos, na ordem de exibição. */
    @Transactional(readOnly = true)
    public List<EventCategoryView> listActive() {
        return repository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(EventCategoryService::toView)
                .toList();
    }

    private static EventCategoryView toView(EventCategory category) {
        return new EventCategoryView(category.getCode(), category.getName());
    }

    public record EventCategoryView(String code, String name) {
    }
}
