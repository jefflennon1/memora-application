package br.com.memora.membership;

import static br.com.memora.support.WebSecuritySupport.asUser;
import static br.com.memora.support.WebSecuritySupport.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.memora.support.EventApi;
import br.com.memora.support.IntegrationTest;
import br.com.memora.support.TestUsers;
import br.com.memora.support.TestUsers.TestUser;
import br.com.memora.support.TestImages;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Entrega 4 — convites nominais e genéricos, solicitações de ingresso e aprovação.
 */
@IntegrationTest
class InvitesIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions createInvite(TestUser actor, UUID eventId, String json) throws Exception {
        return mvc.perform(post("/api/v1/events/{id}/invites", eventId).with(asUser(actor)).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String inviteToken(TestUser actor, UUID eventId, String json) throws Exception {
        String body = createInvite(actor, eventId, json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private ResultActions join(TestUser user, String token) throws Exception {
        return join(user, token, "ATTENDING");
    }

    private ResultActions join(TestUser user, String token, String attendance) throws Exception {
        return mvc.perform(post("/api/v1/invites/{token}/join", token).with(asUser(user)).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"attendanceStatus\":\"" + attendance + "\"}"));
    }

    private ResultActions viewEvent(TestUser user, UUID eventId) throws Exception {
        return mvc.perform(get("/api/v1/events/{id}", eventId).with(asUser(user)));
    }

    // ------------------------------------------------------------------ convite nominal

    @Test
    void namedInviteLetsTheInviteeJoinDirectlyOnce() throws Exception {
        TestUser ana = users.active();
        TestUser bia = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Aniversário da vó", "PRIVATE");

        String body = createInvite(ana, eventId, "{\"type\":\"NAMED\",\"username\":\"@" + bia.username() + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("NAMED"))
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.invitee.username").value(bia.username()))
                .andExpect(jsonPath("$.url", Matchers.startsWith("http://localhost:3000/invites/")))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");

        // Bia vê o convite nos recebidos e o abre.
        mvc.perform(get("/api/v1/invites/received").with(asUser(bia)))
                .andExpect(jsonPath("$[*].token", Matchers.hasItem(token)));
        mvc.perform(get("/api/v1/invites/{token}", token).with(asUser(bia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myStatus").value("CAN_JOIN"))
                .andExpect(jsonPath("$.event.name").value("Aniversário da vó"))
                .andExpect(jsonPath("$.event.privacy").value("PRIVATE"))
                .andExpect(jsonPath("$.event.description").doesNotExist());

        join(bia, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("JOINED"))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()));

        viewEvent(bia, eventId).andExpect(status().isOk()).andExpect(jsonPath("$.myRole").value("PARTICIPANT"));
        mvc.perform(get("/api/v1/events/{id}/participants", eventId).with(asUser(ana)))
                .andExpect(jsonPath("$.items[1].attendanceStatus").value("ATTENDING"));

        // Repetir é seguro.
        join(bia, token).andExpect(jsonPath("$.status").value("ALREADY_PARTICIPANT"));
    }

    @Test
    void namedInviteIsInvisibleToEveryoneElse() throws Exception {
        TestUser ana = users.active();
        TestUser bia = users.active();
        TestUser intruder = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Família", "PRIVATE");
        String token = inviteToken(ana, eventId, "{\"type\":\"NAMED\",\"username\":\"" + bia.username() + "\"}");

        mvc.perform(get("/api/v1/invites/{token}", token).with(asUser(intruder)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVITE_NOT_FOUND"));
        join(intruder, token).andExpect(status().isNotFound());
        viewEvent(intruder, eventId).andExpect(status().isNotFound());
    }

    @Test
    void namedInviteValidatesTheInvitee() throws Exception {
        TestUser ana = users.active();
        UUID eventId = EventApi.create(mvc, ana, "X", "PRIVATE");

        createInvite(ana, eventId, "{\"type\":\"NAMED\",\"username\":\"ninguem" + EventApi.uniqueToken() + "\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        createInvite(ana, eventId, "{\"type\":\"NAMED\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITEE_REQUIRED"));
        createInvite(ana, eventId, "{\"type\":\"NAMED\",\"username\":\"" + ana.username() + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_PARTICIPANT"));
        createInvite(ana, eventId, "{\"type\":\"LINK\",\"username\":\"" + ana.username() + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVITEE_NOT_ALLOWED"));
        createInvite(ana, eventId, "{\"type\":\"LINK\",\"expiresInDays\":91}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INVITE_EXPIRATION"));
    }

    // ------------------------------------------------------------------ convite genérico + aprovação

    @Test
    void customizedInviteIsPublicEditableAndServesItsBackground() throws Exception {
        TestUser ana = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Festa da família", "PRIVATE");
        String json = """
                {"type":"LINK","page":{"title":"Venha celebrar","body":"Uma noite especial.",
                "footer":"Com carinho","location":"Praça da Liberdade, Belo Horizonte",
                "backgroundColor":"#123456","backgroundGradientColor":"#654321",
                "backgroundImageLayout":"REPEAT"}}
                """;
        String created = createInvite(ana, eventId, json)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.page.title").value("Venha celebrar"))
                .andExpect(jsonPath("$.page.backgroundImageLayout").value("REPEAT"))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(created, "$.token");
        String inviteId = JsonPath.read(created, "$.id");

        mvc.perform(get("/api/v1/invites/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.body").value("Uma noite especial."))
                .andExpect(jsonPath("$.page.location").value("Praça da Liberdade, Belo Horizonte"));

        mvc.perform(patch("/api/v1/events/{eventId}/invites/{inviteId}", eventId, inviteId)
                        .with(asUser(ana)).with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"page":{"title":"Título novo","body":"Texto novo","footer":null,
                                "location":null,"backgroundColor":"#abcdef",
                                "backgroundGradientColor":null,"backgroundImageLayout":"COVER"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.title").value("Título novo"));

        MockMultipartFile file = new MockMultipartFile("file", "fundo.png", "image/png",
                TestImages.png(32, 24));
        mvc.perform(multipart(HttpMethod.PUT,
                        "/api/v1/events/{eventId}/invites/{inviteId}/background", eventId, inviteId)
                        .file(file).with(asUser(ana)).with(xsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.backgroundImageUrl", Matchers.containsString(token)));

        mvc.perform(get("/api/v1/invites/{token}/background", token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    void guestCanDeclineAndAcceptLater() throws Exception {
        TestUser ana = users.active();
        TestUser bia = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Celebração", "PRIVATE");
        String token = inviteToken(ana, eventId, "{}");

        mvc.perform(post("/api/v1/invites/{token}/decline", token).with(asUser(bia)).with(xsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
        mvc.perform(get("/api/v1/invites/{token}", token).with(asUser(bia)))
                .andExpect(jsonPath("$.myStatus").value("DECLINED"));

        join(bia, token).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        mvc.perform(get("/api/v1/invites/{token}", token).with(asUser(bia)))
                .andExpect(jsonPath("$.myStatus").value("PENDING_APPROVAL"));
    }

    @Test
    void genericLinkCreatesARequestThatTheOrganizationApproves() throws Exception {
        TestUser ana = users.active();
        TestUser carla = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Casamento", "PRIVATE");
        String token = inviteToken(ana, eventId, "{\"type\":\"QR_CODE\"}");

        mvc.perform(get("/api/v1/invites/{token}", token).with(asUser(carla)))
                .andExpect(jsonPath("$.myStatus").value("CAN_REQUEST"))
                .andExpect(jsonPath("$.type").value("QR_CODE"));

        String joinBody = join(carla, token, "NOT_ATTENDING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(joinBody, "$.requestId");

        // Pedir de novo não duplica.
        join(carla, token, "NOT_ATTENDING").andExpect(jsonPath("$.requestId").value(requestId));
        // Ainda não vê o evento privado.
        viewEvent(carla, eventId).andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/events/{id}/join-requests", eventId).with(asUser(ana)))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(requestId))
                .andExpect(jsonPath("$.items[0].person.username").value(carla.username()))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                .andExpect(jsonPath("$.items[0].attendanceStatus").value("NOT_ATTENDING"));

        mvc.perform(post("/api/v1/events/{id}/join-requests/{r}/approve", eventId, requestId)
                        .with(asUser(ana)).with(xsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        viewEvent(carla, eventId).andExpect(status().isOk()).andExpect(jsonPath("$.myRole").value("PARTICIPANT"));
        mvc.perform(get("/api/v1/events/{id}/participants", eventId).with(asUser(ana)))
                .andExpect(jsonPath("$.items[1].attendanceStatus").value("NOT_ATTENDING"));

        mvc.perform(post("/api/v1/events/{id}/join-requests/{r}/approve", eventId, requestId)
                        .with(asUser(ana)).with(xsrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("JOIN_REQUEST_ALREADY_DECIDED"));
    }

    @Test
    void inPersonInviteRequiresAttendanceButOnlineInviteDoesNot() throws Exception {
        TestUser ana = users.active();
        TestUser bia = users.active();
        UUID inPerson = EventApi.create(mvc, ana, "Almoço", "PRIVATE");
        String inPersonToken = inviteToken(ana, inPerson, "{}");

        mvc.perform(post("/api/v1/invites/{token}/join", inPersonToken).with(asUser(bia)).with(xsrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_REQUIRED"));

        UUID online = EventApi.create(mvc, ana, """
                {"name":"Encontro online","startDate":"%s","modality":"ONLINE","privacy":"PRIVATE"}
                """.formatted(EventApi.future()));
        String onlineToken = inviteToken(ana, online, "{}");
        mvc.perform(post("/api/v1/invites/{token}/join", onlineToken).with(asUser(bia)).with(xsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    void rejectedRequestCannotBeRepeatedButANamedInviteStillWorks() throws Exception {
        TestUser ana = users.active();
        TestUser dan = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Chá", "PRIVATE");
        String token = inviteToken(ana, eventId, "{}");
        String requestId = JsonPath.read(join(dan, token).andReturn().getResponse().getContentAsString(),
                "$.requestId");

        mvc.perform(post("/api/v1/events/{id}/join-requests/{r}/reject", eventId, requestId)
                        .with(asUser(ana)).with(xsrf()))
                .andExpect(jsonPath("$.status").value("REJECTED"));

        join(dan, token).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(get("/api/v1/invites/{token}", token).with(asUser(dan)))
                .andExpect(jsonPath("$.myStatus").value("REJECTED"));
        viewEvent(dan, eventId).andExpect(status().isNotFound());

        String named = inviteToken(ana, eventId, "{\"type\":\"NAMED\",\"username\":\"" + dan.username() + "\"}");
        join(dan, named).andExpect(jsonPath("$.status").value("JOINED"));
    }

    @Test
    void coOrganizerManagesInvitesAndRequestsButParticipantsCannot() throws Exception {
        TestUser ana = users.active();
        TestUser co = users.active();
        TestUser guest = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Formatura", "PRIVATE");
        EventApi.addParticipant(jdbc, eventId, co, "CO_ORGANIZER");
        EventApi.addParticipant(jdbc, eventId, guest, "PARTICIPANT");

        createInvite(co, eventId, "{\"type\":\"LINK\"}").andExpect(status().isCreated());
        mvc.perform(get("/api/v1/events/{id}/join-requests", eventId).with(asUser(co))).andExpect(status().isOk());

        createInvite(guest, eventId, "{\"type\":\"LINK\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVENT_ORGANIZATION_ONLY"));
        mvc.perform(get("/api/v1/events/{id}/invites", eventId).with(asUser(guest)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/events/{id}/join-requests", eventId).with(asUser(guest)))
                .andExpect(status().isForbidden());

        // Quem não participa de um evento privado não descobre nada.
        createInvite(users.active(), eventId, "{\"type\":\"LINK\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    // ------------------------------------------------------------------ validade

    @Test
    void revokedExpiredAndUsedInvitesAreGone() throws Exception {
        TestUser ana = users.active();
        TestUser bia = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Viagem", "PRIVATE");

        String revokedBody = createInvite(ana, eventId, "{}").andReturn().getResponse().getContentAsString();
        mvc.perform(delete("/api/v1/events/{id}/invites/{i}", eventId, (String) JsonPath.read(revokedBody, "$.id"))
                        .with(asUser(ana)).with(xsrf()))
                .andExpect(status().isNoContent());
        join(bia, JsonPath.read(revokedBody, "$.token"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITE_REVOKED"));

        String expired = inviteToken(ana, eventId, "{}");
        jdbc.update("update event_invite set created_at = now() - interval '40 days', "
                + "expires_at = now() - interval '1 day' where token = ?", expired);
        mvc.perform(get("/api/v1/invites/{token}", expired).with(asUser(bia)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITE_EXPIRED"));

        String named = inviteToken(ana, eventId, "{\"type\":\"NAMED\",\"username\":\"" + bia.username() + "\"}");
        join(bia, named).andExpect(jsonPath("$.status").value("JOINED"));
        // Saiu do evento e tenta reutilizar o convite nominal.
        mvc.perform(delete("/api/v1/events/{id}/participants/{u}", eventId, bia.id()).with(asUser(bia)).with(xsrf()))
                .andExpect(status().isNoContent());
        join(bia, named)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITE_ALREADY_USED"));

        mvc.perform(get("/api/v1/invites/{token}", "nao-existe").with(asUser(bia)))
                .andExpect(status().isNotFound());
    }

    @Test
    void inviteCanBeOpenedBeforeOnboardingButJoiningRequiresIt() throws Exception {
        TestUser ana = users.active();
        TestUser newcomer = users.pending();
        UUID eventId = EventApi.create(mvc, ana, "Reencontro", "PRIVATE");
        String token = inviteToken(ana, eventId, "{}");

        mvc.perform(get("/api/v1/invites/{token}", token).with(asUser(newcomer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myStatus").value("CAN_REQUEST"));
        join(newcomer, token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ONBOARDING_REQUIRED"));

        mvc.perform(get("/api/v1/invites/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.title").value("Você está convidado para Reencontro"));
    }
}
