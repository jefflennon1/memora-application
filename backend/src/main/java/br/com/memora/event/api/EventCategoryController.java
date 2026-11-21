package br.com.memora.event.api;

import br.com.memora.event.application.EventCategoryService;
import br.com.memora.identity.web.AllowIncompleteOnboarding;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/event-categories")
@Tag(name = "Categorias de evento", description = "Catálogo de categorias usado para classificar e buscar eventos.")
public class EventCategoryController {

    private final EventCategoryService service;

    public EventCategoryController(EventCategoryService service) {
        this.service = service;
    }

    @GetMapping
    @AllowIncompleteOnboarding
    @Operation(summary = "Lista as categorias de evento disponíveis",
            description = "Endpoint público. Retorna as categorias ativas na ordem sugerida de exibição.")
    public List<EventCategoryResponse> list() {
        return service.listActive().stream()
                .map(v -> new EventCategoryResponse(v.code(), v.name()))
                .toList();
    }

    @Schema(name = "EventCategory")
    public record EventCategoryResponse(
            @Schema(description = "Código estável da categoria", example = "BABY_SHOWER") String code,
            @Schema(description = "Nome exibido", example = "Chá de bebê / Chá de fraldas") String name) {
    }
}
