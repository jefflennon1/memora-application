package br.com.memora.interaction.api;

import br.com.memora.identity.security.MemoraPrincipal;
import br.com.memora.identity.web.AllowIncompleteOnboarding;
import br.com.memora.interaction.application.CommentService;
import br.com.memora.interaction.application.CommentService.CommentView;
import br.com.memora.interaction.application.CommentService.MentionView;
import br.com.memora.interaction.application.CommentService.WriteCommand;
import br.com.memora.interaction.domain.CommentVisibility;
import br.com.memora.membership.api.PersonResponse;
import br.com.memora.memory.api.MemoryController.MediaResponse;
import br.com.memora.memory.application.MediaService;
import br.com.memora.memory.application.MediaService.MediaContent;
import br.com.memora.shared.config.OpenApiConfig;
import br.com.memora.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Comentários", description = "Comentários e respostas (dois níveis) nas memórias, com @menções.")
public class CommentController {

    private final CommentService comments;
    private final MediaService media;

    public CommentController(CommentService comments, MediaService media) {
        this.comments = comments;
        this.media = media;
    }

    @PostMapping("/memories/{memoryId}/comments")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    @Operation(summary = "Comenta uma memória",
            description = "Participante, com o mural aberto. Texto de até 200 caracteres e/ou áudio de até 30 s. "
                    + "`@usuario` de quem participa do "
                    + "evento vira uma menção. Visibilidade padrão: PARTICIPANTS_ONLY; PUBLIC só em memória "
                    + "pública de evento público.")
    public ResponseEntity<CommentResponse> comment(@AuthenticationPrincipal MemoraPrincipal principal,
                                                   @PathVariable UUID memoryId,
                                                   @RequestBody WriteCommentRequest body) {
        CommentView created = comments.comment(memoryId, principal.userId(), body.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/comments/" + created.id()))
                .body(CommentResponse.from(created));
    }

    @GetMapping("/memories/{memoryId}/comments")
    @AllowIncompleteOnboarding
    @Operation(summary = "Comentários de uma memória",
            description = "Comentários principais, paginados, em ordem cronológica, com a contagem de respostas. "
                    + "Quem não participa do evento vê só os públicos.")
    public PageResponse<CommentResponse> list(@AuthenticationPrincipal MemoraPrincipal principal,
                                              @PathVariable UUID memoryId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(comments.list(memoryId, viewer(principal), page, size), CommentResponse::from);
    }

    @GetMapping("/comments/{id}")
    @AllowIncompleteOnboarding
    @Operation(summary = "Consulta um comentário ou resposta",
            description = "Comentário que você não pode ver responde 404.")
    public CommentResponse get(@AuthenticationPrincipal MemoraPrincipal principal, @PathVariable UUID id) {
        return CommentResponse.from(comments.get(id, viewer(principal)));
    }

    @GetMapping("/comments/{id}/audio")
    @AllowIncompleteOnboarding
    @Operation(summary = "Reproduz o áudio de um comentário",
            description = "Aplica as mesmas regras de visibilidade do comentário. Caso contrário, responde 404.")
    public ResponseEntity<InputStreamResource> audio(@AuthenticationPrincipal MemoraPrincipal principal,
                                                      @PathVariable UUID id,
                                                      @RequestHeader(value = HttpHeaders.RANGE, required = false)
                                                      String range,
                                                      WebRequest request) throws IOException {
        UUID mediaId = comments.audioId(id, viewer(principal));
        MediaContent content = media.openCommentAudio(mediaId, id, range);
        String etag = "\"" + content.id() + "\"";
        CacheControl cache = CacheControl.maxAge(Duration.ofHours(1)).cachePrivate();
        if (request.checkNotModified(etag)) {
            content.object().content().close();
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cache).build();
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(
                        content.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .eTag(etag).cacheControl(cache)
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.object().length())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .header("Cross-Origin-Resource-Policy", "same-site");
        if (content.partial()) {
            response.header(HttpHeaders.CONTENT_RANGE, "bytes " + content.start() + "-" + content.end() + "/"
                    + content.totalLength());
        }
        return response.body(new InputStreamResource(content.object().content()));
    }

    @PostMapping("/comments/{id}/replies")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    @Operation(summary = "Responde a um comentário",
            description = "Participante, com o mural aberto. Só se responde a um comentário principal: não existe "
                    + "resposta de resposta. PUBLIC só se o comentário também for público.")
    public ResponseEntity<CommentResponse> reply(@AuthenticationPrincipal MemoraPrincipal principal,
                                                 @PathVariable UUID id,
                                                 @RequestBody WriteCommentRequest body) {
        CommentView created = comments.reply(id, principal.userId(), body.toCommand());
        return ResponseEntity.created(URI.create("/api/v1/comments/" + created.id()))
                .body(CommentResponse.from(created));
    }

    @GetMapping("/comments/{id}/replies")
    @AllowIncompleteOnboarding
    @Operation(summary = "Respostas de um comentário", description = "Paginadas, em ordem cronológica.")
    public PageResponse<CommentResponse> replies(@AuthenticationPrincipal MemoraPrincipal principal,
                                                 @PathVariable UUID id,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(comments.replies(id, viewer(principal), page, size), CommentResponse::from);
    }

    @PatchMapping("/comments/{id}")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    @Operation(summary = "Edita um comentário ou resposta",
            description = "Só o autor, com o mural aberto. Envie só o que mudar. Um comentário que deixa de ser "
                    + "público leva junto as respostas dele.")
    public CommentResponse update(@AuthenticationPrincipal MemoraPrincipal principal, @PathVariable UUID id,
                                  @RequestBody WriteCommentRequest body) {
        return CommentResponse.from(comments.update(id, principal.userId(), body.toCommand()));
    }

    @DeleteMapping("/comments/{id}")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    @Operation(summary = "Exclui um comentário ou resposta",
            description = "Só o autor, mesmo com o mural fechado. Excluir um comentário apaga as respostas dele.")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal MemoraPrincipal principal, @PathVariable UUID id) {
        comments.delete(id, principal.userId());
        return ResponseEntity.noContent().build();
    }

    private static UUID viewer(MemoraPrincipal principal) {
        return principal == null ? null : principal.userId();
    }

    // ---------------------------------------------------------------- DTOs

    @Schema(name = "WriteCommentRequest", description = "Na edição, campos ausentes mantêm o valor atual.")
    public record WriteCommentRequest(
            @Schema(description = "Até 200 caracteres", example = "Que foto linda, @bia.lima! 😍") String text,
            @Schema(description = "Padrão: PARTICIPANTS_ONLY") CommentVisibility visibility,
            @Schema(description = "Áudio pendente, sempre com até 30 segundos") UUID audioId,
            @Schema(description = "Na edição, remove o áudio atual") boolean removeAudio) {

        WriteCommand toCommand() {
            return new WriteCommand(text, visibility, audioId, removeAudio);
        }
    }

    @Schema(name = "Comment")
    public record CommentResponse(
            UUID id,
            UUID memoryId,
            @Schema(description = "Comentário principal a que esta resposta se refere (ausente num comentário)")
            UUID parentId,
            PersonResponse author,
            @Schema(description = "Texto com o @usuário atual de cada pessoa mencionada") String text,
            @Schema(description = "Trechos de `text` que são menções, em ordem") List<MentionResponse> mentions,
            MediaResponse audio,
            CommentVisibility visibility,
            @Schema(description = "Respostas que você pode ver (só em comentário principal)") Long replyCount,
            @Schema(description = "Data de publicação; nunca muda") Instant createdAt,
            @Schema(description = "Última edição, se houver") Instant editedAt,
            @Schema(description = "Se você é o autor") boolean mine) {

        static CommentResponse from(CommentView v) {
            return new CommentResponse(v.id(), v.memoryId(), v.parentId(), PersonResponse.from(v.author()), v.text(),
                    v.mentions().stream().map(MentionResponse::from).toList(), MediaResponse.from(v.audio()),
                    v.visibility(), v.replyCount(),
                    v.createdAt(), v.editedAt(), v.mine());
        }
    }

    @Schema(name = "Mention", description = "Posições em unidades UTF-16 (as de `String.prototype.slice` em "
            + "JavaScript). O trecho é \"@usuario\", \"Perfil privado\" ou \"Usuário excluído\".")
    public record MentionResponse(int start, int length, PersonResponse person) {

        static MentionResponse from(MentionView v) {
            return new MentionResponse(v.start(), v.length(), PersonResponse.from(v.person()));
        }
    }
}
