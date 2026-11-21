package br.com.memora.memory.api;

import br.com.memora.identity.security.MemoraPrincipal;
import br.com.memora.identity.web.AllowIncompleteOnboarding;
import br.com.memora.membership.api.PersonResponse;
import br.com.memora.memory.application.MediaService.MediaView;
import br.com.memora.memory.application.MemoryEngagement.ReactionCount;
import br.com.memora.memory.application.MemoryEngagement.ReactionSummary;
import br.com.memora.memory.application.MemoryService;
import br.com.memora.memory.application.MemoryService.MemoryView;
import br.com.memora.memory.application.MemoryService.MuralOrder;
import br.com.memora.memory.domain.MemoryVisibility;
import br.com.memora.memory.domain.MediaKind;
import br.com.memora.shared.config.OpenApiConfig;
import br.com.memora.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Memórias", description = "Publicações no mural de um evento: texto e imagens.")
public class MemoryController {

    private final MemoryService memories;

    public MemoryController(MemoryService memories) {
        this.memories = memories;
    }

    @PostMapping("/events/{eventId}/memories")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    @Operation(summary = "Publica uma memória",
            description = "Participante, com o mural aberto. Texto (até 500 caracteres) e/ou até 10 mídias enviadas "
                    + "antes por `POST /events/{eventId}/media`, na ordem desejada. Visibilidade padrão: "
                    + "PARTICIPANTS_ONLY; PUBLIC só em evento público.")
    public ResponseEntity<MemoryResponse> publish(@AuthenticationPrincipal MemoraPrincipal principal,
                                                  @PathVariable UUID eventId,
                                                  @Valid @RequestBody PublishMemoryRequest body) {
        MemoryView created = memories.publish(eventId, principal.userId(),
                new MemoryService.PublishCommand(body.text(), body.mediaIds(), body.visibility()));
        return ResponseEntity.created(URI.create("/api/v1/memories/" + created.id()))
                .body(MemoryResponse.from(created));
    }

    @GetMapping("/events/{eventId}/memories")
    @AllowIncompleteOnboarding
    @Operation(summary = "Mural do evento",
            description = "Paginado. Padrão: ordem cronológica de publicação (`order=OLDEST`); `order=NEWEST` inverte. "
                    + "Participantes veem todas as memórias; os demais, só as públicas de evento público.")
    public PageResponse<MemoryResponse> mural(@AuthenticationPrincipal MemoraPrincipal principal,
                                              @PathVariable UUID eventId,
                                              @Parameter(description = "OLDEST (padrão) ou NEWEST")
                                              @RequestParam(defaultValue = "OLDEST") MuralOrder order,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(memories.mural(eventId, viewer(principal), order, page, size), MemoryResponse::from);
    }

    @GetMapping("/memories/{id}")
    @AllowIncompleteOnboarding
    @Operation(summary = "Consulta uma memória", description = "Memória que você não pode ver responde 404.")
    public MemoryResponse get(@AuthenticationPrincipal MemoraPrincipal principal, @PathVariable UUID id) {
        return MemoryResponse.from(memories.get(id, viewer(principal)));
    }

    @PatchMapping("/memories/{id}")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    @Operation(summary = "Edita uma memória",
            description = "Só o autor, com o mural aberto. Envie só o que mudar. `text: null` apaga o texto. "
                    + "`mediaIds` substitui a lista de imagens (na ordem dada): imagens que saírem são apagadas, e "
                    + "novas precisam ter sido enviadas antes. A data de publicação não muda.")
    public MemoryResponse update(@AuthenticationPrincipal MemoraPrincipal principal, @PathVariable UUID id,
                                 @Valid @RequestBody UpdateMemoryRequest body) {
        return MemoryResponse.from(memories.update(id, principal.userId(),
                new MemoryService.UpdateCommand(body.getText(), body.getVisibility(), body.getMediaIds())));
    }

    @DeleteMapping("/memories/{id}")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    @Operation(summary = "Exclui uma memória",
            description = "Só o autor, mesmo com o mural fechado. As imagens são apagadas junto.")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal MemoraPrincipal principal, @PathVariable UUID id) {
        memories.delete(id, principal.userId());
        return ResponseEntity.noContent().build();
    }

    private static UUID viewer(MemoraPrincipal principal) {
        return principal == null ? null : principal.userId();
    }

    // ---------------------------------------------------------------- DTOs

    @Schema(name = "PublishMemoryRequest")
    public record PublishMemoryRequest(
            @Schema(description = "Até 500 caracteres", example = "Que tarde linda! 💛") String text,
            @Schema(description = "Mídias enviadas antes, na ordem de exibição (até 10; no máximo um áudio)") List<UUID> mediaIds,
            @Schema(description = "Padrão: PARTICIPANTS_ONLY") MemoryVisibility visibility) {
    }

    /** Classe com setters: distingue {@code text} ausente (mantém) de {@code null} (apaga). */
    @Schema(name = "UpdateMemoryRequest")
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static class UpdateMemoryRequest {

        @Schema(implementation = String.class, nullable = true, description = "null apaga o texto")
        private Optional<String> text;

        private MemoryVisibility visibility;

        @Schema(description = "Nova lista completa de imagens, na ordem de exibição")
        private List<UUID> mediaIds;

        public Optional<String> getText() {
            return text;
        }

        public void setText(Optional<String> text) {
            this.text = text == null ? Optional.empty() : text;
        }

        public MemoryVisibility getVisibility() {
            return visibility;
        }

        public void setVisibility(MemoryVisibility visibility) {
            this.visibility = visibility;
        }

        public List<UUID> getMediaIds() {
            return mediaIds;
        }

        public void setMediaIds(List<UUID> mediaIds) {
            this.mediaIds = mediaIds;
        }
    }

    @Schema(name = "Memory")
    public record MemoryResponse(UUID id, UUID eventId, PersonResponse author, String text,
                                 MemoryVisibility visibility, List<MediaResponse> media,
                                 @Schema(description = "Data de publicação; nunca muda") Instant createdAt,
                                 @Schema(description = "Última edição, se houver") Instant editedAt,
                                 @Schema(description = "Se você é o autor") boolean mine,
                                 @Schema(description = "Comentários e respostas que você pode ver") long commentCount,
                                 ReactionSummaryResponse reactions) {

        static MemoryResponse from(MemoryView v) {
            return new MemoryResponse(v.id(), v.eventId(), PersonResponse.from(v.author()), v.text(), v.visibility(),
                    v.media().stream().map(MediaResponse::from).toList(), v.createdAt(), v.editedAt(), v.mine(),
                    v.engagement().commentCount(), ReactionSummaryResponse.from(v.engagement().reactions()));
        }
    }

    @Schema(name = "ReactionSummary", description = "Quantas reações há de cada emoji. Nunca quem reagiu.")
    public record ReactionSummaryResponse(
            @Schema(description = "Total de reações") long total,
            @Schema(description = "Contagem por emoji, mais usados primeiro") List<ReactionCountResponse> items,
            @Schema(description = "A sua reação, se houver", example = "❤️") String mine) {

        public static ReactionSummaryResponse from(ReactionSummary s) {
            return new ReactionSummaryResponse(s.total(), s.items().stream().map(ReactionCountResponse::from).toList(),
                    s.mine());
        }
    }

    @Schema(name = "ReactionCount")
    public record ReactionCountResponse(@Schema(example = "❤️") String emoji, long count) {

        static ReactionCountResponse from(ReactionCount c) {
            return new ReactionCountResponse(c.emoji(), c.count());
        }
    }

    @Schema(name = "Media")
    public record MediaResponse(UUID id,
                                @Schema(description = "Caminho na API para baixar o arquivo (enviar com credenciais)",
                                        example = "/api/v1/media/0b6f…") String url,
                                MediaKind kind, String contentType, Integer width, Integer height,
                                Integer durationMs, long sizeBytes) {

        public static MediaResponse from(MediaView v) {
            if (v == null) return null;
            return new MediaResponse(v.id(), v.url(), v.kind(), v.contentType(), v.width(), v.height(),
                    v.durationMs(), v.sizeBytes());
        }
    }
}
