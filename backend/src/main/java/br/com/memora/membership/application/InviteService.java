package br.com.memora.membership.application;

import br.com.memora.event.application.EventAccess;
import br.com.memora.event.application.EventService;
import br.com.memora.event.application.EventService.EventPreview;
import br.com.memora.event.domain.EventModality;
import br.com.memora.identity.domain.User;
import br.com.memora.identity.domain.UserRepository;
import br.com.memora.membership.application.PeopleDirectory.PersonView;
import br.com.memora.membership.domain.EventInvite;
import br.com.memora.membership.domain.EventInviteRepository;
import br.com.memora.membership.domain.AttendanceStatus;
import br.com.memora.membership.domain.InviteBackgroundLayout;
import br.com.memora.membership.domain.InviteResponse;
import br.com.memora.membership.domain.InviteResponseRepository;
import br.com.memora.membership.domain.InviteState;
import br.com.memora.membership.domain.InviteType;
import br.com.memora.membership.domain.JoinRequest;
import br.com.memora.membership.domain.JoinRequestRepository;
import br.com.memora.membership.domain.JoinRequestStatus;
import br.com.memora.membership.domain.ParticipantRole;
import br.com.memora.profile.domain.Username;
import br.com.memora.memory.application.MediaProperties;
import br.com.memora.memory.domain.ImageInspector;
import br.com.memora.shared.config.MemoraProperties;
import br.com.memora.shared.error.ConflictException;
import br.com.memora.shared.error.GoneException;
import br.com.memora.shared.error.NotFoundException;
import br.com.memora.shared.error.PayloadTooLargeException;
import br.com.memora.shared.error.ValidationException;
import br.com.memora.shared.web.PageResponse;
import br.com.memora.shared.storage.AfterCommitStorageCleanup;
import br.com.memora.shared.storage.ObjectStorage;
import br.com.memora.shared.storage.StoredImage;
import br.com.memora.shared.storage.StoredObjectNotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Convites e solicitações de ingresso.
 *
 * <ul>
 *   <li>Convite nominal ({@code NAMED}): só a conta convidada consegue usá-lo; ela entra direto. Para qualquer
 *       outra pessoa o token responde como inexistente.</li>
 *   <li>Convite genérico ({@code LINK} / {@code QR_CODE}): quem entra gera uma solicitação pendente que a
 *       organização aprova ou rejeita. Uma solicitação rejeitada não pode ser refeita pelo mesmo caminho; a
 *       organização pode então enviar um convite nominal.</li>
 * </ul>
 */
@Service
public class InviteService {

    public static final int DEFAULT_VALIDITY_DAYS = 30;
    public static final int MAX_VALIDITY_DAYS = 90;

    private final EventInviteRepository invites;
    private final JoinRequestRepository requests;
    private final InviteResponseRepository responses;
    private final EventAccess access;
    private final EventService events;
    private final ParticipationService participations;
    private final PeopleDirectory people;
    private final UserRepository users;
    private final MemoraProperties properties;
    private final ObjectStorage storage;
    private final AfterCommitStorageCleanup cleanup;
    private final MediaProperties mediaProperties;
    private final Clock clock;

    public InviteService(EventInviteRepository invites, JoinRequestRepository requests,
                         InviteResponseRepository responses, EventAccess access,
                         EventService events, ParticipationService participations, PeopleDirectory people,
                         UserRepository users, MemoraProperties properties, ObjectStorage storage,
                         AfterCommitStorageCleanup cleanup, MediaProperties mediaProperties, Clock clock) {
        this.invites = invites;
        this.requests = requests;
        this.responses = responses;
        this.access = access;
        this.events = events;
        this.participations = participations;
        this.people = people;
        this.users = users;
        this.properties = properties;
        this.storage = storage;
        this.cleanup = cleanup;
        this.mediaProperties = mediaProperties;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ organização: convites

    @Transactional
    public InviteView create(UUID eventId, UUID actorId, CreateInviteCommand command) {
        access.requireVisible(eventId, actorId).requireOrganization();
        InviteType type = command.type() == null ? InviteType.LINK : command.type();
        int days = command.expiresInDays() == null ? DEFAULT_VALIDITY_DAYS : command.expiresInDays();
        if (days < 1 || days > MAX_VALIDITY_DAYS) {
            throw new ValidationException("INVALID_INVITE_EXPIRATION",
                    "A validade do convite vai de 1 a " + MAX_VALIDITY_DAYS + " dias.");
        }

        UUID inviteeId = null;
        if (type == InviteType.NAMED) {
            inviteeId = requireInvitee(command.username()).getId();
            if (participations.roleOf(eventId, inviteeId).isPresent()) {
                throw new ConflictException("ALREADY_PARTICIPANT", "Esta pessoa já participa do evento.");
            }
        } else if (command.username() != null && !command.username().isBlank()) {
            throw new ValidationException("INVITEE_NOT_ALLOWED",
                    "Só o convite nominal (NAMED) é destinado a uma pessoa.");
        }

        EventInvite invite = EventInvite.create(eventId, type, inviteeId, actorId,
                Duration.ofDays(days), clock.instant());
        EventPreview event = events.previewForInvite(eventId, actorId);
        applyPage(invite, command.page() == null ? defaultPage(event) : command.page());
        invites.save(invite);
        return toViews(List.of(invite)).getFirst();
    }

    @Transactional
    public InviteView updatePage(UUID eventId, UUID inviteId, UUID actorId, InvitePageCommand page) {
        access.requireVisible(eventId, actorId).requireOrganization();
        EventInvite invite = findManaged(eventId, inviteId);
        applyPage(invite, page);
        return toViews(List.of(invite)).getFirst();
    }

    @Transactional
    public InviteView uploadBackground(UUID eventId, UUID inviteId, UUID actorId, byte[] content) {
        ImageInspector.ImageInfo info = inspectImage(content);
        access.requireVisible(eventId, actorId).requireOrganization();
        EventInvite invite = findManaged(eventId, inviteId);
        String oldKey = invite.getBackgroundStorageKey();
        String key = "invites/" + inviteId + "/background/" + UUID.randomUUID();
        invite.replaceBackground(key, info.contentType(), clock.instant());
        invites.flush();
        storage.put(key, content, info.contentType());
        if (oldKey != null) cleanup.deleteAfterCommit(List.of(oldKey));
        return toViews(List.of(invite)).getFirst();
    }

    @Transactional
    public InviteView removeBackground(UUID eventId, UUID inviteId, UUID actorId) {
        access.requireVisible(eventId, actorId).requireOrganization();
        EventInvite invite = findManaged(eventId, inviteId);
        String oldKey = invite.getBackgroundStorageKey();
        invite.removeBackground();
        invites.flush();
        if (oldKey != null) cleanup.deleteAfterCommit(List.of(oldKey));
        return toViews(List.of(invite)).getFirst();
    }

    @Transactional(readOnly = true)
    public StoredImage openBackground(String token) {
        EventInvite invite = invites.findByToken(token == null ? "" : token)
                .orElseThrow(() -> new NotFoundException("INVITE_NOT_FOUND", "Convite não encontrado."));
        requireActive(invite);
        if (invite.getBackgroundStorageKey() == null) throw backgroundNotFound();
        try {
            return new StoredImage(invite.getBackgroundStorageKey(), invite.getBackgroundContentType(),
                    storage.get(invite.getBackgroundStorageKey()));
        } catch (StoredObjectNotFoundException ex) {
            throw backgroundNotFound();
        }
    }

    @Transactional(readOnly = true)
    public Page<InviteView> list(UUID eventId, UUID actorId, int page, int size) {
        access.requireVisible(eventId, actorId).requireOrganization();
        Page<EventInvite> result = invites.findByEventIdOrderByCreatedAtDesc(eventId, PageResponse.request(page, size));
        Map<UUID, InviteView> views = toViews(result.getContent()).stream()
                .collect(Collectors.toMap(InviteView::id, Function.identity()));
        return result.map(i -> views.get(i.getId()));
    }

    /** Revoga o convite. Quem já entrou continua no evento; solicitações já feitas continuam valendo. */
    @Transactional
    public void revoke(UUID eventId, UUID inviteId, UUID actorId) {
        access.requireVisible(eventId, actorId).requireOrganization();
        EventInvite invite = invites.findByIdAndEventId(inviteId, eventId)
                .orElseThrow(() -> new NotFoundException("INVITE_NOT_FOUND", "Convite não encontrado."));
        invite.revoke(clock.instant());
    }

    // ------------------------------------------------------------------ quem recebeu o convite

    /** O que a pessoa vê ao abrir o link do convite. */
    @Transactional(readOnly = true)
    public InvitePreview preview(String token, UUID viewerId) {
        EventInvite invite = loadForPreview(token, viewerId);
        Optional<ParticipantRole> role = viewerId == null ? Optional.empty()
                : participations.roleOf(invite.getEventId(), viewerId);
        MyInviteStatus status;
        if (role.isPresent()) {
            status = MyInviteStatus.PARTICIPANT;
        } else {
            requireActive(invite);
            status = viewerId != null && responses.findByInviteIdAndUserId(invite.getId(), viewerId).isPresent()
                    ? MyInviteStatus.DECLINED
                    : invite.getType().requiresApproval() && viewerId != null
                        ? statusOfRequest(invite.getEventId(), viewerId)
                        : invite.getType().requiresApproval() ? MyInviteStatus.CAN_REQUEST : MyInviteStatus.CAN_JOIN;
        }
        return new InvitePreview(invite.getToken(), invite.getType(), invite.getExpiresAt(),
                events.previewForInvite(invite.getEventId(), viewerId), status, pageOf(invite));
    }

    /** Usa o convite: entra direto (nominal) ou pede para entrar (genérico). Repetir a chamada é seguro. */
    @Transactional
    public JoinResult join(String token, UUID userId, AttendanceStatus requestedAttendance) {
        EventInvite invite = loadUsable(token, userId);
        UUID eventId = invite.getEventId();
        if (participations.roleOf(eventId, userId).isPresent()) {
            return new JoinResult(JoinOutcome.ALREADY_PARTICIPANT, eventId, null);
        }
        requireActive(invite);
        responses.deleteByInviteIdAndUserId(invite.getId(), userId);
        EventPreview event = events.previewForInvite(eventId, userId);
        AttendanceStatus attendance = resolveAttendance(event, requestedAttendance);
        Instant now = clock.instant();

        if (!invite.getType().requiresApproval()) {
            participations.addParticipant(eventId, userId, attendance);
            invite.markUsed(now);
            // Um pedido pendente anterior perde o sentido: a organização convidou a pessoa diretamente.
            requests.findFirstByEventIdAndUserIdOrderByRequestedAtDesc(eventId, userId)
                    .filter(r -> r.getStatus() == JoinRequestStatus.PENDING)
                    .ifPresent(r -> r.approve(invite.getCreatedBy(), now));
            return new JoinResult(JoinOutcome.JOINED, eventId, null);
        }

        Optional<JoinRequest> latest = requests.findFirstByEventIdAndUserIdOrderByRequestedAtDesc(eventId, userId);
        if (latest.isPresent() && latest.get().getStatus() == JoinRequestStatus.PENDING) {
            latest.get().updateAttendance(attendance, now);
            return new JoinResult(JoinOutcome.PENDING_APPROVAL, eventId, latest.get().getId());
        }
        if (latest.isPresent() && latest.get().getStatus() == JoinRequestStatus.REJECTED) {
            return new JoinResult(JoinOutcome.REJECTED, eventId, latest.get().getId());
        }
        JoinRequest request = requests.save(JoinRequest.pending(eventId, userId, invite.getId(), attendance, now));
        return new JoinResult(JoinOutcome.PENDING_APPROVAL, eventId, request.getId());
    }

    @Transactional
    public JoinResult decline(String token, UUID userId) {
        EventInvite invite = loadUsable(token, userId);
        UUID eventId = invite.getEventId();
        if (participations.roleOf(eventId, userId).isPresent()) {
            return new JoinResult(JoinOutcome.ALREADY_PARTICIPANT, eventId, null);
        }
        requireActive(invite);
        responses.findByInviteIdAndUserId(invite.getId(), userId)
                .orElseGet(() -> responses.save(InviteResponse.declined(invite.getId(), userId, clock.instant())));
        return new JoinResult(JoinOutcome.DECLINED, eventId, null);
    }

    /** Convites nominais válidos recebidos pela pessoa. */
    @Transactional(readOnly = true)
    public List<InvitePreview> received(UUID userId) {
        List<InvitePreview> result = new ArrayList<>();
        for (EventInvite invite : invites.findActiveNamedFor(userId, clock.instant())) {
            if (participations.roleOf(invite.getEventId(), userId).isEmpty()) {
                result.add(new InvitePreview(invite.getToken(), invite.getType(), invite.getExpiresAt(),
                        events.previewForInvite(invite.getEventId(), userId), MyInviteStatus.CAN_JOIN,
                        pageOf(invite)));
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ organização: solicitações

    @Transactional(readOnly = true)
    public Page<JoinRequestView> listRequests(UUID eventId, UUID actorId, JoinRequestStatus status, int page,
                                              int size) {
        access.requireVisible(eventId, actorId).requireOrganization();
        Page<JoinRequest> result = requests.findByEventIdAndStatusOrderByRequestedAtAscIdAsc(eventId,
                status == null ? JoinRequestStatus.PENDING : status, PageResponse.request(page, size));
        Map<UUID, PersonView> persons = people.revealed(result.getContent().stream().map(JoinRequest::getUserId)
                .toList());
        return result.map(r -> toView(r, persons.get(r.getUserId())));
    }

    @Transactional
    public JoinRequestView approve(UUID eventId, UUID requestId, UUID actorId) {
        access.requireVisible(eventId, actorId).requireOrganization();
        JoinRequest request = loadRequest(eventId, requestId);
        request.approve(actorId, clock.instant());
        participations.addParticipant(eventId, request.getUserId(), request.getAttendanceStatus());
        return toView(request, people.revealed(List.of(request.getUserId())).get(request.getUserId()));
    }

    @Transactional
    public JoinRequestView reject(UUID eventId, UUID requestId, UUID actorId) {
        access.requireVisible(eventId, actorId).requireOrganization();
        JoinRequest request = loadRequest(eventId, requestId);
        request.reject(actorId, clock.instant());
        return toView(request, people.revealed(List.of(request.getUserId())).get(request.getUserId()));
    }

    // ------------------------------------------------------------------ apoio

    /** Convite inexistente, ou nominal para outra pessoa: mesma resposta, para não revelar nada. */
    private EventInvite loadUsable(String token, UUID viewerId) {
        return invites.findByToken(token == null ? "" : token)
                .filter(i -> i.getType() != InviteType.NAMED || i.isNamedFor(viewerId))
                .orElseThrow(() -> new NotFoundException("INVITE_NOT_FOUND", "Convite não encontrado."));
    }

    private EventInvite loadForPreview(String token, UUID viewerId) {
        return invites.findByToken(token == null ? "" : token)
                .filter(i -> viewerId == null || i.getType() != InviteType.NAMED || i.isNamedFor(viewerId))
                .orElseThrow(() -> new NotFoundException("INVITE_NOT_FOUND", "Convite não encontrado."));
    }

    private EventInvite findManaged(UUID eventId, UUID inviteId) {
        return invites.findByIdAndEventId(inviteId, eventId)
                .orElseThrow(() -> new NotFoundException("INVITE_NOT_FOUND", "Convite não encontrado."));
    }

    private void applyPage(EventInvite invite, InvitePageCommand page) {
        if (page == null) throw new ValidationException("INVITE_PAGE_REQUIRED", "Informe os dados da página do convite.");
        String title = requiredText(page.title(), 120, "INVALID_INVITE_PAGE_TITLE", "Informe um título de até 120 caracteres.");
        String body = requiredText(page.body(), 3000, "INVALID_INVITE_PAGE_BODY", "Informe um texto de até 3000 caracteres.");
        String color = requireColor(page.backgroundColor(), "INVALID_INVITE_BACKGROUND_COLOR");
        String gradient = optionalColor(page.backgroundGradientColor(), "INVALID_INVITE_BACKGROUND_COLOR");
        invite.customizePage(title, body, optionalText(page.footer(), 500, "INVALID_INVITE_PAGE_FOOTER"),
                optionalText(page.location(), 300, "INVALID_INVITE_PAGE_LOCATION"), color, gradient,
                page.backgroundImageLayout() == null ? InviteBackgroundLayout.COVER : page.backgroundImageLayout());
    }

    private static InvitePageCommand defaultPage(EventPreview event) {
        return new InvitePageCommand("Você está convidado para " + event.name(),
                "Esperamos você para compartilhar este momento com a gente.", null, event.city(),
                "#163f47", "#256a75", InviteBackgroundLayout.COVER);
    }

    private InvitePageView pageOf(EventInvite invite) {
        String backgroundUrl = invite.getBackgroundStorageKey() == null ? null
                : "/api/v1/invites/" + invite.getToken() + "/background?v="
                    + invite.getBackgroundUpdatedAt().toEpochMilli();
        return new InvitePageView(invite.getPageTitle(), invite.getPageBody(), invite.getPageFooter(),
                invite.getPageLocation(), invite.getBackgroundColor(), invite.getBackgroundGradientColor(),
                backgroundUrl, invite.getBackgroundImageLayout());
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

    private static String requiredText(String value, int max, String code, String message) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty() || normalized.length() > max) throw new ValidationException(code, message);
        return normalized;
    }

    private static String optionalText(String value, int max, String code) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > max) throw new ValidationException(code, "Use até " + max + " caracteres.");
        return normalized;
    }

    private static String requireColor(String color, String code) {
        if (color == null || !color.matches("#[0-9a-fA-F]{6}")) {
            throw new ValidationException(code, "Informe uma cor no formato #RRGGBB.");
        }
        return color.toLowerCase();
    }

    private static String optionalColor(String color, String code) {
        return color == null || color.isBlank() ? null : requireColor(color, code);
    }

    private static NotFoundException backgroundNotFound() {
        return new NotFoundException("INVITE_BACKGROUND_NOT_FOUND", "Background do convite não encontrado.");
    }

    private void requireActive(EventInvite invite) {
        InviteState state = invite.stateAt(clock.instant());
        switch (state) {
            case ACTIVE -> {
            }
            case EXPIRED -> throw new GoneException("INVITE_EXPIRED",
                    "Este convite expirou. Peça um novo à organização do evento.");
            case REVOKED -> throw new GoneException("INVITE_REVOKED",
                    "Este convite foi cancelado pela organização do evento.");
            case USED -> throw new GoneException("INVITE_ALREADY_USED", "Este convite já foi utilizado.");
        }
    }

    private MyInviteStatus statusOfRequest(UUID eventId, UUID userId) {
        return requests.findFirstByEventIdAndUserIdOrderByRequestedAtDesc(eventId, userId)
                .map(r -> switch (r.getStatus()) {
                    case PENDING -> MyInviteStatus.PENDING_APPROVAL;
                    case REJECTED -> MyInviteStatus.REJECTED;
                    // Aprovado no passado e removido depois: pode pedir de novo.
                    case APPROVED -> MyInviteStatus.CAN_REQUEST;
                })
                .orElse(MyInviteStatus.CAN_REQUEST);
    }

    private User requireInvitee(String rawUsername) {
        if (rawUsername == null || rawUsername.isBlank()) {
            throw new ValidationException("INVITEE_REQUIRED", "Informe o @usuário de quem será convidado.");
        }
        return users.findByUsername(Username.normalize(rawUsername))
                .filter(User::isActive)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Nenhuma conta com este @usuário."));
    }

    private JoinRequest loadRequest(UUID eventId, UUID requestId) {
        return requests.findByIdAndEventId(requestId, eventId)
                .orElseThrow(() -> new NotFoundException("JOIN_REQUEST_NOT_FOUND", "Solicitação não encontrada."));
    }

    private static AttendanceStatus resolveAttendance(EventPreview event, AttendanceStatus requested) {
        if (event.modality() == EventModality.IN_PERSON) {
            if (requested == null) {
                throw new ValidationException("ATTENDANCE_REQUIRED",
                        "Confirme se você comparecerá presencialmente ao evento.");
            }
            return requested;
        }
        if (requested != null) {
            throw new ValidationException("ATTENDANCE_NOT_APPLICABLE",
                    "Confirmação de presença física só se aplica a eventos presenciais.");
        }
        return null;
    }

    private List<InviteView> toViews(List<EventInvite> list) {
        List<UUID> ids = new ArrayList<>();
        list.forEach(i -> {
            ids.add(i.getCreatedBy());
            if (i.getInviteeUserId() != null) {
                ids.add(i.getInviteeUserId());
            }
        });
        Map<UUID, PersonView> persons = people.revealed(ids);
        Instant now = clock.instant();
        String base = properties.frontend().normalizedOrigin() + "/invites/";
        return list.stream().map(i -> new InviteView(i.getId(), i.getType(), i.getToken(), base + i.getToken(),
                i.stateAt(now), i.getInviteeUserId() == null ? null : persons.get(i.getInviteeUserId()),
                persons.get(i.getCreatedBy()), i.getCreatedAt(), i.getExpiresAt(), i.getRevokedAt(),
                i.getUsedAt(), pageOf(i))).toList();
    }

    private static JoinRequestView toView(JoinRequest r, PersonView person) {
        return new JoinRequestView(r.getId(), person, r.getStatus(), r.getRequestedAt(), r.getDecidedAt(),
                r.getAttendanceStatus(), r.getAttendanceUpdatedAt());
    }

    // ------------------------------------------------------------------ contratos

    public record CreateInviteCommand(InviteType type, String username, Integer expiresInDays,
                                      InvitePageCommand page) {
    }

    public record InvitePageCommand(String title, String body, String footer, String location,
                                    String backgroundColor, String backgroundGradientColor,
                                    InviteBackgroundLayout backgroundImageLayout) {
    }

    public record InvitePageView(String title, String body, String footer, String location,
                                 String backgroundColor, String backgroundGradientColor,
                                 String backgroundImageUrl, InviteBackgroundLayout backgroundImageLayout) {
    }

    public record InviteView(UUID id, InviteType type, String token, String url, InviteState state,
                             PersonView invitee, PersonView createdBy, Instant createdAt, Instant expiresAt,
                             Instant revokedAt, Instant usedAt, InvitePageView page) {
    }

    public enum MyInviteStatus {
        /** Convite nominal válido: ao aceitar, entra direto. */
        CAN_JOIN,
        /** Convite genérico: ao aceitar, gera uma solicitação. */
        CAN_REQUEST,
        PENDING_APPROVAL,
        REJECTED,
        DECLINED,
        PARTICIPANT
    }

    public record InvitePreview(String token, InviteType type, Instant expiresAt, EventPreview event,
                                MyInviteStatus myStatus, InvitePageView page) {
    }

    public enum JoinOutcome {
        JOINED,
        ALREADY_PARTICIPANT,
        PENDING_APPROVAL,
        REJECTED,
        DECLINED
    }

    public record JoinResult(JoinOutcome status, UUID eventId, UUID requestId) {
    }

    public record JoinRequestView(UUID id, PersonView person, JoinRequestStatus status, Instant requestedAt,
                                  Instant decidedAt, AttendanceStatus attendanceStatus,
                                  Instant attendanceUpdatedAt) {
    }
}
