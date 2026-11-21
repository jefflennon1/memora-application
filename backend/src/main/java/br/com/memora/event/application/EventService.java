package br.com.memora.event.application;

import br.com.memora.event.application.EventAccess.EventContext;
import br.com.memora.event.application.EventAccess.Visible;
import br.com.memora.event.domain.Event;
import br.com.memora.event.domain.Event.EventDetails;
import br.com.memora.event.domain.EventCategory;
import br.com.memora.event.domain.EventCategoryRepository;
import br.com.memora.event.domain.EventLocation;
import br.com.memora.event.domain.EventModality;
import br.com.memora.event.domain.EventPrivacy;
import br.com.memora.event.domain.EventRepository;
import br.com.memora.event.domain.EventSchedule;
import br.com.memora.event.domain.MuralStatus;
import br.com.memora.membership.application.ParticipationService;
import br.com.memora.membership.application.PeopleDirectory;
import br.com.memora.membership.application.PeopleDirectory.PersonView;
import br.com.memora.membership.domain.ParticipantRole;
import br.com.memora.membership.domain.AttendanceStatus;
import br.com.memora.memory.application.MediaProperties;
import br.com.memora.memory.domain.ImageInspector;
import br.com.memora.shared.error.NotFoundException;
import br.com.memora.shared.error.PayloadTooLargeException;
import br.com.memora.shared.error.ValidationException;
import br.com.memora.shared.storage.AfterCommitStorageCleanup;
import br.com.memora.shared.storage.ObjectStorage;
import br.com.memora.shared.storage.StoredImage;
import br.com.memora.shared.storage.StoredObjectNotFoundException;
import br.com.memora.shared.web.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de eventos. Visibilidade e permissões passam por {@link EventAccess}.
 */
@Service
public class EventService {

    /** Fuso mais atrasado do planeta: o "hoje" dele é o mais antigo que ainda é hoje em algum lugar. */
    private static final ZoneOffset EARLIEST_TODAY_ZONE = ZoneOffset.ofHours(-12);

    /** Mais recentes primeiro; desempate estável para a paginação. */
    private static final Sort LISTING_ORDER = Sort.by(Sort.Order.desc("startDate"), Sort.Order.desc("createdAt"),
            Sort.Order.asc("id"));

    private final EventRepository events;
    private final EventCategoryRepository categories;
    private final EventAccess access;
    private final ParticipationService participations;
    private final PeopleDirectory people;
    private final ObjectStorage storage;
    private final AfterCommitStorageCleanup cleanup;
    private final MediaProperties mediaProperties;
    private final Clock clock;

    public EventService(EventRepository events, EventCategoryRepository categories, EventAccess access,
                        ParticipationService participations, PeopleDirectory people, ObjectStorage storage,
                        AfterCommitStorageCleanup cleanup, MediaProperties mediaProperties, Clock clock) {
        this.events = events;
        this.categories = categories;
        this.access = access;
        this.participations = participations;
        this.people = people;
        this.storage = storage;
        this.cleanup = cleanup;
        this.mediaProperties = mediaProperties;
        this.clock = clock;
    }

    @Transactional
    public EventView create(UUID organizerId, CreateEventCommand command) {
        EventDetails details = new EventDetails(command.name(), command.description(),
                requireActiveCategory(command.categoryCode()),
                new EventSchedule(command.startDate(), command.startTime(), command.endDate(), command.endTime()),
                new EventLocation(command.modality(), command.city(), command.address()));
        EventPrivacy privacy = command.privacy() == null ? EventPrivacy.PRIVATE : command.privacy();
        Event event = events.save(Event.create(organizerId, details, privacy, earliestAllowedStart(), clock.instant()));
        participations.registerOrganizer(event.getId(), organizerId,
                command.modality() == EventModality.IN_PERSON ? AttendanceStatus.ATTENDING : null);
        return toView(event, ParticipantRole.ORGANIZER, organizerId);
    }

    /** @param viewerId pessoa que consulta; {@code null} para visitante anônimo */
    @Transactional(readOnly = true)
    public EventView get(UUID eventId, UUID viewerId) {
        Visible visible = access.loadVisible(eventId, viewerId);
        return toView(visible.event(), visible.role().orElse(null), viewerId);
    }

    @Transactional(readOnly = true)
    public Page<EventView> mine(UUID userId, int page, int size) {
        return toViews(events.findParticipating(userId, ordered(page, size)), userId);
    }

    /** Busca de eventos públicos. Eventos privados nunca aparecem, nem para seus participantes. */
    @Transactional(readOnly = true)
    public Page<EventView> searchPublic(PublicSearchQuery query, UUID viewerId, int page, int size) {
        Page<Event> result = events.findAll(
                EventRepository.PublicSearch.of(query.name(), query.city(), query.category()), ordered(page, size));
        return toViews(result, viewerId);
    }

    /** Edição parcial pela organização (organizador ou coorganizador). Privacidade: só o organizador. */
    @Transactional
    public EventView update(UUID eventId, UUID userId, UpdateEventCommand command) {
        Visible visible = access.loadVisible(eventId, userId);
        ParticipantRole role = visible.role()
                .filter(ParticipantRole::canManageEvent)
                .orElseThrow(EventContext::organizationOnly);
        Event event = visible.event();

        if (command.privacy() != null && command.privacy() != event.getPrivacy()) {
            if (!role.isOrganizer()) {
                throw EventContext.organizerOnly();
            }
            event.changePrivacy(command.privacy());
        }

        EventDetails current = event.details();
        EventSchedule schedule = current.schedule();
        EventLocation location = current.location();
        if (command.modality() != null && command.modality() != location.modality()) {
            participations.clearAttendance(eventId);
        }
        EventDetails changed = new EventDetails(
                command.name() != null ? command.name() : current.name(),
                pick(command.description(), current.description()),
                command.categoryCode() != null
                        ? requireActiveCategory(command.categoryCode().orElse(null))
                        : current.categoryCode(),
                new EventSchedule(
                        command.startDate() != null ? command.startDate() : schedule.startDate(),
                        pick(command.startTime(), schedule.startTime()),
                        pick(command.endDate(), schedule.endDate()),
                        pick(command.endTime(), schedule.endTime())),
                new EventLocation(
                        command.modality() != null ? command.modality() : location.modality(),
                        pick(command.city(), location.city()),
                        pick(command.address(), location.address())));
        event.update(changed, earliestAllowedStart());
        events.flush();
        return toView(event, role, userId);
    }

    @Transactional
    public EventView uploadCover(UUID eventId, UUID userId, byte[] content) {
        ImageInspector.ImageInfo info = inspectImage(content);
        Visible visible = access.loadVisible(eventId, userId);
        ParticipantRole role = visible.role().filter(ParticipantRole::canManageEvent)
                .orElseThrow(EventContext::organizationOnly);
        Event event = visible.event();
        String oldKey = event.getCoverStorageKey();
        String key = "events/" + eventId + "/cover/" + UUID.randomUUID();
        event.replaceCover(key, info.contentType(), content.length, info.width(), info.height(), clock.instant());
        events.flush();
        storage.put(key, content, info.contentType());
        if (oldKey != null) cleanup.deleteAfterCommit(List.of(oldKey));
        return toView(event, role, userId);
    }

    @Transactional
    public EventView removeCover(UUID eventId, UUID userId) {
        Visible visible = access.loadVisible(eventId, userId);
        ParticipantRole role = visible.role().filter(ParticipantRole::canManageEvent)
                .orElseThrow(EventContext::organizationOnly);
        Event event = visible.event();
        String oldKey = event.getCoverStorageKey();
        event.removeCover();
        events.flush();
        if (oldKey != null) cleanup.deleteAfterCommit(List.of(oldKey));
        return toView(event, role, userId);
    }

    @Transactional(readOnly = true)
    public StoredImage openCover(UUID eventId, UUID viewerId) {
        Event event = access.loadVisible(eventId, viewerId).event();
        if (event.getCoverStorageKey() == null) throw coverNotFound();
        try {
            return new StoredImage(event.getCoverStorageKey(), event.getCoverContentType(),
                    storage.get(event.getCoverStorageKey()));
        } catch (StoredObjectNotFoundException ex) {
            throw coverNotFound();
        }
    }

    /** Abre ou fecha o mural. Somente o organizador principal. */
    @Transactional
    public EventView changeMuralStatus(UUID eventId, UUID userId, MuralStatus status) {
        Visible visible = access.loadVisible(eventId, userId);
        ParticipantRole role = visible.role()
                .filter(ParticipantRole::isOrganizer)
                .orElseThrow(EventContext::organizerOnly);
        visible.event().changeMuralStatus(status, clock.instant());
        events.flush();
        return toView(visible.event(), role, userId);
    }

    /**
     * Dados mínimos do evento para quem recebeu um convite decidir se entra. <b>Não confere a visibilidade</b>:
     * só pode ser chamado depois de validar um token de convite, que é o que autoriza conhecer o evento.
     */
    @Transactional(readOnly = true)
    public EventPreview previewForInvite(UUID eventId, UUID viewerId) {
        Event e = events.findById(eventId).orElseThrow(EventAccess::notFound);
        CategoryView category = e.getCategoryCode() == null ? null
                : new CategoryView(e.getCategoryCode(), categoryNames().get(e.getCategoryCode()));
        return new EventPreview(e.getId(), e.getName(), category, e.getStartDate(), e.getStartTime(),
                e.getEndDate(), e.getModality(), e.getCity(), e.getPrivacy(),
                people.summary(viewerId, e.getOrganizerId()));
    }

    // ------------------------------------------------------------------ apoio

    private String requireActiveCategory(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return categories.findById(code.strip())
                .filter(EventCategory::isActive)
                .map(EventCategory::getCode)
                .orElseThrow(() -> new ValidationException("INVALID_EVENT_CATEGORY",
                        "Categoria de evento inexistente. Consulte GET /api/v1/event-categories."));
    }

    private ImageInspector.ImageInfo inspectImage(byte[] content) {
        if (content == null || content.length == 0) {
            throw new ValidationException("EMPTY_FILE", "O arquivo enviado está vazio.");
        }
        if (content.length > mediaProperties.maxImageBytes()) {
            throw new PayloadTooLargeException("MEDIA_TOO_LARGE", "A imagem pode ter no máximo "
                    + mediaProperties.maxImageBytes() / (1024 * 1024) + " MB.");
        }
        return ImageInspector.inspect(content);
    }

    private static NotFoundException coverNotFound() {
        return new NotFoundException("EVENT_COVER_NOT_FOUND", "Foto de capa não encontrada.");
    }

    private LocalDate earliestAllowedStart() {
        return LocalDate.now(clock.withZone(EARLIEST_TODAY_ZONE));
    }

    private static Pageable ordered(int page, int size) {
        Pageable requested = PageResponse.request(page, size);
        return PageRequest.of(requested.getPageNumber(), requested.getPageSize(), LISTING_ORDER);
    }

    /** Campo de PATCH que pode ser apagado: ausente mantém, {@code Optional.empty()} apaga, valor substitui. */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static <T> T pick(Optional<T> patch, T current) {
        return patch == null ? current : patch.orElse(null);
    }

    private Page<EventView> toViews(Page<Event> page, UUID viewerId) {
        List<Event> content = page.getContent();
        Map<UUID, ParticipantRole> roles = participations.rolesOf(viewerId,
                content.stream().map(Event::getId).toList());
        Map<UUID, PersonView> organizers = people.summaries(viewerId,
                content.stream().map(Event::getOrganizerId).toList());
        Map<String, String> categoryNames = categoryNames();
        return page.map(e -> toView(e, roles.get(e.getId()), organizers.get(e.getOrganizerId()), categoryNames));
    }

    private EventView toView(Event event, ParticipantRole role, UUID viewerId) {
        return toView(event, role, people.summary(viewerId, event.getOrganizerId()), categoryNames());
    }

    private Map<String, String> categoryNames() {
        return categories.findAll().stream().collect(Collectors.toMap(EventCategory::getCode, EventCategory::getName));
    }

    private static EventView toView(Event e, ParticipantRole role, PersonView organizer,
                                    Map<String, String> categoryNames) {
        CategoryView category = e.getCategoryCode() == null ? null
                : new CategoryView(e.getCategoryCode(), categoryNames.get(e.getCategoryCode()));
        return new EventView(e.getId(), e.getName(), e.getDescription(), coverUrl(e), category, e.getStartDate(),
                e.getStartTime(), e.getEndDate(), e.getEndTime(), e.getModality(), e.getCity(), e.getAddress(),
                e.getPrivacy(), e.getMuralStatus(), e.getMuralStatusChangedAt(), organizer, role,
                e.getCreatedAt(), e.getUpdatedAt());
    }

    private static String coverUrl(Event event) {
        return event.getCoverStorageKey() == null ? null
                : "/api/v1/events/" + event.getId() + "/cover?v=" + event.getCoverUpdatedAt().toEpochMilli();
    }

    // ------------------------------------------------------------------ contratos

    public record CreateEventCommand(String name, String description, String categoryCode, LocalDate startDate,
                                     LocalTime startTime, LocalDate endDate, LocalTime endTime,
                                     EventModality modality, String city, String address, EventPrivacy privacy) {
    }

    /**
     * Alteração parcial. Campos obrigatórios: {@code null} mantém o valor atual. Campos opcionais
     * ({@link Optional}): {@code null} mantém, {@code Optional.empty()} apaga, valor substitui.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public record UpdateEventCommand(String name, Optional<String> description, Optional<String> categoryCode,
                                     LocalDate startDate, Optional<LocalTime> startTime,
                                     Optional<LocalDate> endDate, Optional<LocalTime> endTime,
                                     EventModality modality, Optional<String> city, Optional<String> address,
                                     EventPrivacy privacy) {
    }

    public record PublicSearchQuery(String name, String city, String category) {
    }

    public record EventView(UUID id, String name, String description, String coverUrl, CategoryView category,
                            LocalDate startDate,
                            LocalTime startTime, LocalDate endDate, LocalTime endTime, EventModality modality,
                            String city, String address, EventPrivacy privacy, MuralStatus muralStatus,
                            Instant muralStatusChangedAt, PersonView organizer, ParticipantRole myRole,
                            Instant createdAt, Instant updatedAt) {
    }

    public record CategoryView(String code, String name) {
    }

    /** O mínimo sobre o evento para decidir sobre um convite (sem descrição nem endereço). */
    public record EventPreview(UUID id, String name, CategoryView category, LocalDate startDate, LocalTime startTime,
                               LocalDate endDate, EventModality modality, String city, EventPrivacy privacy,
                               PersonView organizer) {
    }
}
