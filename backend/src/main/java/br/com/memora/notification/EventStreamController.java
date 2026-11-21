package br.com.memora.notification;

import br.com.memora.identity.security.MemoraPrincipal;
import br.com.memora.shared.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Tempo real", description = "Avisos leves do mural, sem carregar o conteudo das memorias.")
public class EventStreamController {

    private final EventStreamService streams;

    public EventStreamController(EventStreamService streams) {
        this.streams = streams;
    }

    @GetMapping(path = "/events/{eventId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    @Operation(summary = "Acompanha novas memorias",
            description = "Somente participantes. Envia memory-created depois do commit, sem o conteudo da memoria.")
    public SseEmitter stream(@AuthenticationPrincipal MemoraPrincipal principal, @PathVariable UUID eventId) {
        return streams.subscribe(eventId, principal.userId());
    }
}
