package br.com.memora.membership.api;

import br.com.memora.identity.security.MemoraPrincipal;
import br.com.memora.membership.application.ParticipantService;
import br.com.memora.membership.application.ParticipantService.ParticipantView;
import br.com.memora.membership.application.ParticipantService.AttendanceSummary;
import br.com.memora.membership.domain.AttendanceStatus;
import br.com.memora.membership.domain.ParticipantRole;
import br.com.memora.shared.config.OpenApiConfig;
import br.com.memora.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events/{eventId}/participants")
@Tag(name = "Participantes", description = "Quem participa do evento e com qual papel.")
@SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
public class ParticipantController {

    private final ParticipantService participants;

    public ParticipantController(ParticipantService participants) {
        this.participants = participants;
    }

    @GetMapping
    @Operation(summary = "Lista os participantes",
            description = "Só para participantes do evento. Organizador primeiro, depois coorganizadores e os demais "
                    + "por ordem de entrada.")
    public PageResponse<ParticipantResponse> list(@AuthenticationPrincipal MemoraPrincipal principal,
                                                  @PathVariable UUID eventId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(participants.list(eventId, principal.userId(), page, size), ParticipantResponse::from);
    }

    @GetMapping("/attendance-summary")
    @Operation(summary = "Resumo das confirmações de presença",
            description = "Em eventos presenciais, mostra quantas pessoas confirmaram, recusaram ou ainda não responderam.")
    public AttendanceSummary attendanceSummary(@AuthenticationPrincipal MemoraPrincipal principal,
                                                @PathVariable UUID eventId) {
        return participants.attendanceSummary(eventId, principal.userId());
    }

    @PatchMapping("/me/attendance")
    @Operation(summary = "Atualiza minha confirmação de presença",
            description = "Cada participante pode alterar a própria resposta em eventos presenciais.")
    public ParticipantResponse updateAttendance(@AuthenticationPrincipal MemoraPrincipal principal,
                                                @PathVariable UUID eventId,
                                                @Valid @RequestBody AttendanceRequest body) {
        return ParticipantResponse.from(participants.updateAttendance(eventId, principal.userId(),
                body.attendanceStatus()));
    }

    @PatchMapping("/{userId}")
    @Operation(summary = "Muda o papel de um participante",
            description = "Somente o organizador: promove a `CO_ORGANIZER` ou rebaixa a `PARTICIPANT`. O organizador "
                    + "principal é permanente.")
    public ParticipantResponse changeRole(@AuthenticationPrincipal MemoraPrincipal principal,
                                          @PathVariable UUID eventId, @PathVariable UUID userId,
                                          @Valid @RequestBody ChangeRoleRequest body) {
        return ParticipantResponse.from(participants.changeRole(eventId, principal.userId(), userId, body.role()));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Remove um participante (ou sai do evento)",
            description = "Com o seu próprio id, você sai do evento. O organizador remove qualquer pessoa; o "
                    + "coorganizador, só participantes comuns. O organizador principal não sai nem é removido. "
                    + "As memórias já publicadas pela pessoa continuam no mural.")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal MemoraPrincipal principal,
                                       @PathVariable UUID eventId, @PathVariable UUID userId) {
        participants.remove(eventId, principal.userId(), userId);
        return ResponseEntity.noContent().build();
    }

    @Schema(name = "ChangeRoleRequest")
    public record ChangeRoleRequest(@Schema(description = "CO_ORGANIZER ou PARTICIPANT") @NotNull ParticipantRole role) {
    }

    @Schema(name = "AttendanceRequest")
    public record AttendanceRequest(@NotNull AttendanceStatus attendanceStatus) {
    }

    @Schema(name = "Participant")
    public record ParticipantResponse(PersonResponse person, ParticipantRole role, Instant joinedAt,
                                      AttendanceStatus attendanceStatus, Instant attendanceUpdatedAt) {

        static ParticipantResponse from(ParticipantView v) {
            return new ParticipantResponse(PersonResponse.from(v.person()), v.role(), v.joinedAt(),
                    v.attendanceStatus(), v.attendanceUpdatedAt());
        }
    }
}
