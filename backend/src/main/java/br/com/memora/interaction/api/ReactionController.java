package br.com.memora.interaction.api;

import br.com.memora.identity.security.MemoraPrincipal;
import br.com.memora.identity.web.AllowIncompleteOnboarding;
import br.com.memora.interaction.application.ReactionService;
import br.com.memora.memory.api.MemoryController.ReactionSummaryResponse;
import br.com.memora.shared.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/memories/{memoryId}/reactions")
@Tag(name = "Reações", description = "Um emoji por pessoa em cada memória. A API mostra quantas reações há de cada "
        + "emoji, nunca quem reagiu.")
public class ReactionController {

    private final ReactionService reactions;

    public ReactionController(ReactionService reactions) {
        this.reactions = reactions;
    }

    @PutMapping
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    @Operation(summary = "Reage a uma memória",
            description = "Participante, com o mural aberto. Reagir de novo troca o emoji. Devolve o resumo atualizado.")
    public ReactionSummaryResponse react(@AuthenticationPrincipal MemoraPrincipal principal,
                                         @PathVariable UUID memoryId, @RequestBody ReactRequest body) {
        return ReactionSummaryResponse.from(reactions.react(memoryId, principal.userId(), body.emoji()));
    }

    @DeleteMapping
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    @Operation(summary = "Remove a sua reação", description = "Também com o mural fechado. Devolve o resumo atualizado.")
    public ReactionSummaryResponse remove(@AuthenticationPrincipal MemoraPrincipal principal,
                                          @PathVariable UUID memoryId) {
        return ReactionSummaryResponse.from(reactions.remove(memoryId, principal.userId()));
    }

    @GetMapping("/summary")
    @AllowIncompleteOnboarding
    @Operation(summary = "Resumo das reações",
            description = "Contagem por emoji (mais usados primeiro) e a sua reação. Nunca a lista de quem reagiu.")
    public ReactionSummaryResponse summary(@AuthenticationPrincipal MemoraPrincipal principal,
                                           @PathVariable UUID memoryId) {
        return ReactionSummaryResponse.from(reactions.summary(memoryId, principal == null ? null : principal.userId()));
    }

    @Schema(name = "ReactRequest")
    public record ReactRequest(@Schema(description = "Exatamente um emoji", example = "❤️") String emoji) {
    }
}
