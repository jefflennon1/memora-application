package br.com.memora.membership.api;

import br.com.memora.event.application.EventService.EventPreview;
import br.com.memora.event.domain.EventModality;
import br.com.memora.event.domain.EventPrivacy;
import br.com.memora.identity.security.MemoraPrincipal;
import br.com.memora.identity.web.AllowIncompleteOnboarding;
import br.com.memora.membership.application.InviteService;
import br.com.memora.membership.application.InviteService.InvitePreview;
import br.com.memora.membership.application.InviteService.InviteView;
import br.com.memora.membership.application.InviteService.JoinOutcome;
import br.com.memora.membership.application.InviteService.JoinRequestView;
import br.com.memora.membership.application.InviteService.MyInviteStatus;
import br.com.memora.membership.application.InviteService.InvitePageView;
import br.com.memora.membership.domain.InviteBackgroundLayout;
import br.com.memora.membership.domain.InviteState;
import br.com.memora.membership.domain.InviteType;
import br.com.memora.membership.domain.AttendanceStatus;
import br.com.memora.membership.domain.JoinRequestStatus;
import br.com.memora.shared.config.OpenApiConfig;
import br.com.memora.shared.web.PageResponse;
import br.com.memora.shared.web.InlineImageResponse;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Convites e solicitações", description = "Trazer pessoas para um evento: convites nominais, links "
        + "genéricos/QR Code e aprovação de quem pediu para entrar.")
@SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
public class InviteController {

    private final InviteService invites;

    public InviteController(InviteService invites) {
        this.invites = invites;
    }

    // ---------------------------------------------------------------- organização: convites

    @PostMapping("/events/{eventId}/invites")
    @Operation(summary = "Cria um convite",
            description = "Organizador ou coorganizador. `NAMED` exige `username` e deixa a pessoa entrar direto "
                    + "(uso único). `LINK` e `QR_CODE` são genéricos: quem entra gera uma solicitação. "
                    + "A resposta traz o `url` pronto para compartilhar (o QR Code é gerado a partir dele).")
    public ResponseEntity<InviteResponse> create(@AuthenticationPrincipal MemoraPrincipal principal,
                                                 @PathVariable UUID eventId,
                                                 @Valid @RequestBody CreateInviteRequest body) {
        InviteView created = invites.create(eventId, principal.userId(),
                new InviteService.CreateInviteCommand(body.type(), body.username(), body.expiresInDays(),
                        body.page() == null ? null : body.page().toCommand()));
        return ResponseEntity.created(URI.create("/api/v1/events/" + eventId + "/invites/" + created.id()))
                .body(InviteResponse.from(created));
    }

    @GetMapping("/events/{eventId}/invites")
    @Operation(summary = "Lista os convites do evento", description = "Organizador ou coorganizador. Mais recentes primeiro.")
    public PageResponse<InviteResponse> list(@AuthenticationPrincipal MemoraPrincipal principal,
                                             @PathVariable UUID eventId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(invites.list(eventId, principal.userId(), page, size), InviteResponse::from);
    }

    @DeleteMapping("/events/{eventId}/invites/{inviteId}")
    @Operation(summary = "Revoga um convite",
            description = "O link deixa de funcionar. Quem já entrou continua no evento.")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal MemoraPrincipal principal,
                                       @PathVariable UUID eventId, @PathVariable UUID inviteId) {
        invites.revoke(eventId, inviteId, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/events/{eventId}/invites/{inviteId}")
    @Operation(summary = "Edita a página personalizada do convite")
    public InviteResponse updatePage(@AuthenticationPrincipal MemoraPrincipal principal,
                                     @PathVariable UUID eventId, @PathVariable UUID inviteId,
                                     @Valid @RequestBody UpdateInviteRequest body) {
        return InviteResponse.from(invites.updatePage(eventId, inviteId, principal.userId(), body.page().toCommand()));
    }

    @PutMapping(path = "/events/{eventId}/invites/{inviteId}/background",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Insere ou substitui o background do convite")
    public InviteResponse uploadBackground(@AuthenticationPrincipal MemoraPrincipal principal,
                                           @PathVariable UUID eventId, @PathVariable UUID inviteId,
                                           @RequestPart("file") MultipartFile file) throws IOException {
        return InviteResponse.from(invites.uploadBackground(eventId, inviteId, principal.userId(), file.getBytes()));
    }

    @DeleteMapping("/events/{eventId}/invites/{inviteId}/background")
    @Operation(summary = "Remove o background enviado do convite")
    public InviteResponse removeBackground(@AuthenticationPrincipal MemoraPrincipal principal,
                                           @PathVariable UUID eventId, @PathVariable UUID inviteId) {
        return InviteResponse.from(invites.removeBackground(eventId, inviteId, principal.userId()));
    }

    // ---------------------------------------------------------------- quem recebeu

    @GetMapping("/invites/received")
    @Operation(summary = "Convites nominais recebidos", description = "Convites válidos destinados a você, de eventos "
            + "dos quais você ainda não participa.")
    public List<InvitePreviewResponse> received(@AuthenticationPrincipal MemoraPrincipal principal) {
        return invites.received(principal.userId()).stream().map(InvitePreviewResponse::from).toList();
    }

    @GetMapping("/invites/{token}")
    @AllowIncompleteOnboarding
    @Operation(summary = "Abre um convite",
            description = "Mostra o mínimo sobre o evento para a pessoa decidir, e a situação dela (`myStatus`). "
                    + "Funciona antes do cadastro inicial; aceitar exige o cadastro concluído. Convite inexistente, ou "
                    + "nominal para outra pessoa: 404. Expirado, revogado ou já usado: 410.")
    public InvitePreviewResponse preview(@AuthenticationPrincipal MemoraPrincipal principal,
                                         @PathVariable String token) {
        return InvitePreviewResponse.from(invites.preview(token, principal == null ? null : principal.userId()));
    }

    @GetMapping("/invites/{token}/background")
    @AllowIncompleteOnboarding
    @Operation(summary = "Visualiza o background do convite")
    public ResponseEntity<InputStreamResource> background(@PathVariable String token, WebRequest request)
            throws IOException {
        return InlineImageResponse.of(invites.openBackground(token), request);
    }

    @PostMapping("/invites/{token}/join")
    @Operation(summary = "Aceita o convite",
            description = "Nominal: entra direto (`JOINED`). Genérico: cria uma solicitação (`PENDING_APPROVAL`). "
                    + "Repetir a chamada é seguro: devolve a situação atual (`ALREADY_PARTICIPANT`, "
                    + "`PENDING_APPROVAL` ou `REJECTED`).")
    public JoinResponse join(@AuthenticationPrincipal MemoraPrincipal principal, @PathVariable String token,
                             @RequestBody(required = false) JoinInviteRequest body) {
        var result = invites.join(token, principal.userId(), body == null ? null : body.attendanceStatus());
        return new JoinResponse(result.status(), result.eventId(), result.requestId());
    }

    @PostMapping("/invites/{token}/decline")
    @Operation(summary = "Recusa o convite", description = "Não inclui a pessoa no evento e permite aceitar depois.")
    public JoinResponse decline(@AuthenticationPrincipal MemoraPrincipal principal, @PathVariable String token) {
        var result = invites.decline(token, principal.userId());
        return new JoinResponse(result.status(), result.eventId(), result.requestId());
    }

    // ---------------------------------------------------------------- organização: solicitações

    @GetMapping("/events/{eventId}/join-requests")
    @Operation(summary = "Lista solicitações de ingresso",
            description = "Organizador ou coorganizador. Padrão: pendentes, mais antigas primeiro.")
    public PageResponse<JoinRequestResponse> requests(@AuthenticationPrincipal MemoraPrincipal principal,
                                                      @PathVariable UUID eventId,
                                                      @Parameter(description = "Padrão: PENDING")
                                                      @RequestParam(required = false) JoinRequestStatus status,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(invites.listRequests(eventId, principal.userId(), status, page, size),
                JoinRequestResponse::from);
    }

    @PostMapping("/events/{eventId}/join-requests/{requestId}/approve")
    @Operation(summary = "Aprova uma solicitação", description = "A pessoa vira participante.")
    public JoinRequestResponse approve(@AuthenticationPrincipal MemoraPrincipal principal,
                                       @PathVariable UUID eventId, @PathVariable UUID requestId) {
        return JoinRequestResponse.from(invites.approve(eventId, requestId, principal.userId()));
    }

    @PostMapping("/events/{eventId}/join-requests/{requestId}/reject")
    @Operation(summary = "Rejeita uma solicitação",
            description = "A pessoa não consegue pedir de novo pelo link genérico; para trazê-la depois, envie um "
                    + "convite nominal.")
    public JoinRequestResponse reject(@AuthenticationPrincipal MemoraPrincipal principal,
                                      @PathVariable UUID eventId, @PathVariable UUID requestId) {
        return JoinRequestResponse.from(invites.reject(eventId, requestId, principal.userId()));
    }

    // ---------------------------------------------------------------- DTOs

    @Schema(name = "CreateInviteRequest")
    public record CreateInviteRequest(
            @Schema(description = "Padrão: LINK") InviteType type,
            @Schema(description = "@usuário do convidado (só para NAMED)", example = "bia.lima")
            @Size(max = 31) String username,
            @Schema(description = "Validade em dias, de 1 a 90 (padrão 30)", example = "30") Integer expiresInDays,
            @Valid InvitePageRequest page) {
    }

    @Schema(name = "UpdateInviteRequest")
    public record UpdateInviteRequest(@Valid @NotNull InvitePageRequest page) {
    }

    @Schema(name = "InvitePageRequest")
    public record InvitePageRequest(String title, String body, String footer, String location,
                                    String backgroundColor, String backgroundGradientColor,
                                    InviteBackgroundLayout backgroundImageLayout) {
        InviteService.InvitePageCommand toCommand() {
            return new InviteService.InvitePageCommand(title, body, footer, location, backgroundColor,
                    backgroundGradientColor, backgroundImageLayout);
        }
    }

    @Schema(name = "JoinInviteRequest")
    public record JoinInviteRequest(
            @Schema(description = "Obrigatório para eventos presenciais: ATTENDING ou NOT_ATTENDING")
            AttendanceStatus attendanceStatus) {
    }

    @Schema(name = "Invite")
    public record InviteResponse(UUID id, InviteType type, String token,
                                 @Schema(description = "Link para compartilhar (página do frontend)") String url,
                                 InviteState state, PersonResponse invitee, PersonResponse createdBy,
                                 Instant createdAt, Instant expiresAt, Instant revokedAt, Instant usedAt,
                                 InvitePageResponse page) {

        static InviteResponse from(InviteView v) {
            return new InviteResponse(v.id(), v.type(), v.token(), v.url(), v.state(),
                    PersonResponse.from(v.invitee()), PersonResponse.from(v.createdBy()), v.createdAt(),
                    v.expiresAt(), v.revokedAt(), v.usedAt(), InvitePageResponse.from(v.page()));
        }
    }

    @Schema(name = "InvitePreview")
    public record InvitePreviewResponse(String token, InviteType type, Instant expiresAt, EventPreviewResponse event,
                                        @Schema(description = "CAN_JOIN, CAN_REQUEST, PENDING_APPROVAL, REJECTED "
                                                + "DECLINED ou PARTICIPANT") MyInviteStatus myStatus,
                                        InvitePageResponse page) {

        static InvitePreviewResponse from(InvitePreview p) {
            return new InvitePreviewResponse(p.token(), p.type(), p.expiresAt(), EventPreviewResponse.from(p.event()),
                    p.myStatus(), InvitePageResponse.from(p.page()));
        }
    }

    @Schema(name = "InvitePage")
    public record InvitePageResponse(String title, String body, String footer, String location,
                                     String backgroundColor, String backgroundGradientColor,
                                     String backgroundImageUrl, InviteBackgroundLayout backgroundImageLayout) {
        static InvitePageResponse from(InvitePageView page) {
            return new InvitePageResponse(page.title(), page.body(), page.footer(), page.location(),
                    page.backgroundColor(), page.backgroundGradientColor(), page.backgroundImageUrl(),
                    page.backgroundImageLayout());
        }
    }

    @Schema(name = "InviteEventPreview", description = "O mínimo sobre o evento (sem descrição nem endereço).")
    public record EventPreviewResponse(UUID id, String name, String categoryCode, String categoryName,
                                       LocalDate startDate,
                                       @Schema(type = "string", format = "time", example = "15:00")
                                       @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                       LocalDate endDate, EventModality modality, String city, EventPrivacy privacy,
                                       PersonResponse organizer) {

        static EventPreviewResponse from(EventPreview e) {
            return new EventPreviewResponse(e.id(), e.name(), e.category() == null ? null : e.category().code(),
                    e.category() == null ? null : e.category().name(), e.startDate(), e.startTime(), e.endDate(),
                    e.modality(), e.city(), e.privacy(), PersonResponse.from(e.organizer()));
        }
    }

    @Schema(name = "JoinResult")
    public record JoinResponse(JoinOutcome status, UUID eventId,
                               @Schema(description = "Solicitação criada ou existente (convites genéricos)")
                               UUID requestId) {
    }

    @Schema(name = "JoinRequest")
    public record JoinRequestResponse(UUID id, PersonResponse person, JoinRequestStatus status, Instant requestedAt,
                                      Instant decidedAt, AttendanceStatus attendanceStatus,
                                      Instant attendanceUpdatedAt) {

        static JoinRequestResponse from(JoinRequestView v) {
            return new JoinRequestResponse(v.id(), PersonResponse.from(v.person()), v.status(), v.requestedAt(),
                    v.decidedAt(), v.attendanceStatus(), v.attendanceUpdatedAt());
        }
    }
}
