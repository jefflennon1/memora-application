package br.com.memora.event.api;

import br.com.memora.event.application.EventService;
import com.fasterxml.jackson.annotation.JsonFormat;
import br.com.memora.event.application.EventService.EventView;
import br.com.memora.event.domain.Event;
import br.com.memora.event.domain.EventLocation;
import br.com.memora.event.domain.EventModality;
import br.com.memora.event.domain.EventPrivacy;
import br.com.memora.event.domain.MuralStatus;
import br.com.memora.identity.security.MemoraPrincipal;
import br.com.memora.identity.web.AllowIncompleteOnboarding;
import br.com.memora.membership.api.PersonResponse;
import br.com.memora.membership.domain.ParticipantRole;
import br.com.memora.shared.config.OpenApiConfig;
import br.com.memora.shared.web.PageResponse;
import br.com.memora.shared.web.InlineImageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Eventos", description = "Criação, consulta, busca pública, edição e estado do mural.")
public class EventController {

    private final EventService events;

    public EventController(EventService events) {
        this.events = events;
    }

    @PostMapping
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    @Operation(summary = "Cria um evento",
            description = "Quem cria vira o organizador (permanente) e participante. O mural nasce aberto. "
                    + "Sem `privacy`, o evento é criado como PRIVATE.")
    public ResponseEntity<EventResponse> create(@AuthenticationPrincipal MemoraPrincipal principal,
                                                @Valid @RequestBody CreateEventRequest body) {
        EventView created = events.create(principal.userId(), new EventService.CreateEventCommand(
                body.name(), body.description(), body.categoryCode(), body.startDate(), body.startTime(),
                body.endDate(), body.endTime(), body.modality(), body.city(), body.address(), body.privacy()));
        return ResponseEntity.created(URI.create("/api/v1/events/" + created.id())).body(EventResponse.from(created));
    }

    @GetMapping("/mine")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    @Operation(summary = "Lista os eventos dos quais você participa",
            description = "Qualquer papel (organizador, coorganizador ou participante). Mais recentes primeiro.")
    public PageResponse<EventResponse> mine(@AuthenticationPrincipal MemoraPrincipal principal,
                                            @Parameter(example = "0") @RequestParam(defaultValue = "0") int page,
                                            @Parameter(description = "Máximo 50", example = "20")
                                            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(events.mine(principal.userId(), page, size), EventResponse::from);
    }

    @GetMapping("/public")
    @AllowIncompleteOnboarding
    @Operation(summary = "Busca eventos públicos",
            description = "Endpoint público. Filtra por trecho do nome, trecho da cidade (sem diferenciar "
                    + "maiúsculas nem acentos) e código da categoria. Eventos privados nunca aparecem.")
    public PageResponse<EventResponse> searchPublic(
            @AuthenticationPrincipal MemoraPrincipal principal,
            @Parameter(description = "Trecho do nome", example = "cha de fraldas") @RequestParam(required = false) String q,
            @Parameter(description = "Trecho da cidade", example = "belo horizonte") @RequestParam(required = false) String city,
            @Parameter(description = "Código da categoria", example = "BABY_SHOWER") @RequestParam(required = false) String category,
            @Parameter(example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Máximo 50", example = "20") @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(events.searchPublic(new EventService.PublicSearchQuery(q, city, category),
                viewer(principal), page, size), EventResponse::from);
    }

    @GetMapping("/{id}")
    @AllowIncompleteOnboarding
    @Operation(summary = "Consulta um evento",
            description = "Evento público: qualquer pessoa vê. Evento privado: só participantes; para os demais "
                    + "a resposta é 404, como se ele não existisse. `myRole` traz o seu papel, se houver.")
    public EventResponse get(@AuthenticationPrincipal MemoraPrincipal principal, @PathVariable UUID id) {
        return EventResponse.from(events.get(id, viewer(principal)));
    }

    @PatchMapping("/{id}")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    @Operation(summary = "Edita um evento",
            description = "Pelo organizador ou coorganizador. Envie só o que mudar; campos opcionais enviados como "
                    + "`null` são apagados. Mudar `privacy` é exclusivo do organizador. A regra \"início não pode "
                    + "estar no passado\" só vale quando `startDate` muda.")
    public EventResponse update(@AuthenticationPrincipal MemoraPrincipal principal, @PathVariable UUID id,
                                @Valid @RequestBody UpdateEventRequest body) {
        return EventResponse.from(events.update(id, principal.userId(), body.toCommand()));
    }

    @PutMapping(path = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    @Operation(summary = "Insere ou substitui a foto de capa",
            description = "Organizador ou coorganizador. Campo multipart `file`; aceita JPEG, PNG, WebP ou GIF.")
    public EventResponse uploadCover(@AuthenticationPrincipal MemoraPrincipal principal, @PathVariable UUID id,
                                     @RequestPart("file") MultipartFile file) throws IOException {
        return EventResponse.from(events.uploadCover(id, principal.userId(), file.getBytes()));
    }

    @DeleteMapping("/{id}/cover")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    @Operation(summary = "Remove a foto de capa", description = "Organizador ou coorganizador.")
    public EventResponse removeCover(@AuthenticationPrincipal MemoraPrincipal principal, @PathVariable UUID id) {
        return EventResponse.from(events.removeCover(id, principal.userId()));
    }

    @GetMapping("/{id}/cover")
    @AllowIncompleteOnboarding
    @Operation(summary = "Visualiza a foto de capa", description = "Respeita a visibilidade pública ou privada do evento.")
    public ResponseEntity<InputStreamResource> cover(@AuthenticationPrincipal MemoraPrincipal principal,
                                                     @PathVariable UUID id, WebRequest request) throws IOException {
        return InlineImageResponse.of(events.openCover(id, viewer(principal)), request);
    }

    @PatchMapping("/{id}/mural")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    @Operation(summary = "Abre ou fecha o mural",
            description = "Somente o organizador. O mural nunca fecha sozinho: um evento que já passou continua "
                    + "recebendo memórias enquanto o mural estiver aberto.")
    public EventResponse changeMural(@AuthenticationPrincipal MemoraPrincipal principal, @PathVariable UUID id,
                                     @Valid @RequestBody MuralRequest body) {
        return EventResponse.from(events.changeMuralStatus(id, principal.userId(), body.status()));
    }

    private static UUID viewer(MemoraPrincipal principal) {
        return principal == null ? null : principal.userId();
    }

    // ---------------------------------------------------------------- DTOs

    @Schema(name = "CreateEventRequest")
    public record CreateEventRequest(
            @Schema(example = "Chá de Fraldas do Liam") @NotBlank @Size(max = Event.NAME_MAX) String name,
            @Schema(example = "Uma tarde para celebrar a chegada do Liam.")
            @Size(max = Event.DESCRIPTION_MAX) String description,
            @Schema(description = "Código de GET /event-categories (opcional)", example = "BABY_SHOWER")
            @Size(max = 40) String categoryCode,
            @Schema(description = "Não pode estar no passado", example = "2026-12-05") @NotNull LocalDate startDate,
            @Schema(type = "string", format = "time", example = "15:00") LocalTime startTime,
            @Schema(description = "Não pode ser anterior ao início", example = "2026-12-05") LocalDate endDate,
            @Schema(type = "string", format = "time", example = "18:00") LocalTime endTime,
            @NotNull EventModality modality,
            @Schema(description = "Só em eventos presenciais", example = "Belo Horizonte")
            @Size(max = EventLocation.CITY_MAX) String city,
            @Schema(description = "Só em eventos presenciais") @Size(max = EventLocation.ADDRESS_MAX) String address,
            @Schema(description = "Padrão: PRIVATE") EventPrivacy privacy) {
    }

    /**
     * Edição parcial. Classe com setters (e não record) de propósito: o Jackson só chama o setter de um campo
     * presente no JSON, o que distingue "ausente" (mantém) de {@code null} (apaga) nos campos {@link Optional}.
     * Em records, um {@link Optional} ausente chegaria como {@code Optional.empty()} e apagaria o valor.
     * Campos não {@link Optional}: ausente ou {@code null} mantêm o valor atual.
     */
    @Schema(name = "UpdateEventRequest",
            description = "Envie só o que mudar. Campos opcionais enviados como null são apagados.")
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static class UpdateEventRequest {

        @Schema(example = "Chá de Fraldas do Liam")
        @Size(max = Event.NAME_MAX)
        private String name;

        @Schema(implementation = String.class, nullable = true)
        private Optional<@Size(max = Event.DESCRIPTION_MAX) String> description;

        @Schema(implementation = String.class, nullable = true, example = "BABY_SHOWER")
        private Optional<@Size(max = 40) String> categoryCode;

        private LocalDate startDate;

        @Schema(type = "string", format = "time", nullable = true, example = "15:00")
        private Optional<LocalTime> startTime;

        @Schema(implementation = LocalDate.class, nullable = true)
        private Optional<LocalDate> endDate;

        @Schema(type = "string", format = "time", nullable = true, example = "18:00")
        private Optional<LocalTime> endTime;

        private EventModality modality;

        @Schema(implementation = String.class, nullable = true)
        private Optional<@Size(max = EventLocation.CITY_MAX) String> city;

        @Schema(implementation = String.class, nullable = true)
        private Optional<@Size(max = EventLocation.ADDRESS_MAX) String> address;

        @Schema(description = "Somente o organizador")
        private EventPrivacy privacy;

        EventService.UpdateEventCommand toCommand() {
            return new EventService.UpdateEventCommand(name, description, categoryCode, startDate, startTime,
                    endDate, endTime, modality, city, address, privacy);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Optional<String> getDescription() {
            return description;
        }

        public void setDescription(Optional<String> description) {
            this.description = present(description);
        }

        public Optional<String> getCategoryCode() {
            return categoryCode;
        }

        public void setCategoryCode(Optional<String> categoryCode) {
            this.categoryCode = present(categoryCode);
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public void setStartDate(LocalDate startDate) {
            this.startDate = startDate;
        }

        public Optional<LocalTime> getStartTime() {
            return startTime;
        }

        public void setStartTime(Optional<LocalTime> startTime) {
            this.startTime = present(startTime);
        }

        public Optional<LocalDate> getEndDate() {
            return endDate;
        }

        public void setEndDate(Optional<LocalDate> endDate) {
            this.endDate = present(endDate);
        }

        public Optional<LocalTime> getEndTime() {
            return endTime;
        }

        public void setEndTime(Optional<LocalTime> endTime) {
            this.endTime = present(endTime);
        }

        public EventModality getModality() {
            return modality;
        }

        public void setModality(EventModality modality) {
            this.modality = modality;
        }

        public Optional<String> getCity() {
            return city;
        }

        public void setCity(Optional<String> city) {
            this.city = present(city);
        }

        public Optional<String> getAddress() {
            return address;
        }

        public void setAddress(Optional<String> address) {
            this.address = present(address);
        }

        public EventPrivacy getPrivacy() {
            return privacy;
        }

        public void setPrivacy(EventPrivacy privacy) {
            this.privacy = privacy;
        }

        /** O setter só é chamado para campos presentes no JSON; um {@code null} explícito significa "apagar". */
        private static <T> Optional<T> present(Optional<T> value) {
            return value == null ? Optional.empty() : value;
        }
    }

    @Schema(name = "MuralRequest")
    public record MuralRequest(@NotNull MuralStatus status) {
    }

    @Schema(name = "Event")
    public record EventResponse(
            UUID id,
            String name,
            String description,
            String coverUrl,
            Category category,
            LocalDate startDate,
            @Schema(type = "string", format = "time", example = "15:00") @JsonFormat(pattern = "HH:mm")
            LocalTime startTime,
            LocalDate endDate,
            @Schema(type = "string", format = "time", example = "18:00") @JsonFormat(pattern = "HH:mm")
            LocalTime endTime,
            EventModality modality,
            String city,
            String address,
            EventPrivacy privacy,
            MuralStatus muralStatus,
            @Schema(description = "Última vez que o mural foi aberto/fechado") Instant muralStatusChangedAt,
            PersonResponse organizer,
            @Schema(description = "Seu papel no evento (ausente se você não participa)") ParticipantRole myRole,
            Instant createdAt,
            Instant updatedAt) {

        static EventResponse from(EventView v) {
            return new EventResponse(v.id(), v.name(), v.description(), v.coverUrl(),
                    v.category() == null ? null : new Category(v.category().code(), v.category().name()),
                    v.startDate(), v.startTime(), v.endDate(), v.endTime(), v.modality(), v.city(), v.address(),
                    v.privacy(), v.muralStatus(), v.muralStatusChangedAt(),
                    PersonResponse.from(v.organizer()),
                    v.myRole(), v.createdAt(), v.updatedAt());
        }
    }

    @Schema(name = "EventCategoryRef")
    public record Category(String code, String name) {
    }
}
