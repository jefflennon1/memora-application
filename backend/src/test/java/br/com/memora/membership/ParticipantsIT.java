package br.com.memora.membership;

import static br.com.memora.support.EventApi.addParticipant;
import static br.com.memora.support.WebSecuritySupport.asUser;
import static br.com.memora.support.WebSecuritySupport.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.memora.support.EventApi;
import br.com.memora.support.IntegrationTest;
import br.com.memora.support.TestUsers;
import br.com.memora.support.TestUsers.TestUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Entrega 4 — lista de participantes, papéis, remoção e privacidade do perfil.
 */
@IntegrationTest
class ParticipantsIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions changeRole(TestUser actor, UUID eventId, TestUser target, String role) throws Exception {
        return mvc.perform(patch("/api/v1/events/{id}/participants/{u}", eventId, target.id())
                .with(asUser(actor)).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"" + role + "\"}"));
    }

    private ResultActions remove(TestUser actor, UUID eventId, TestUser target) throws Exception {
        return mvc.perform(delete("/api/v1/events/{id}/participants/{u}", eventId, target.id())
                .with(asUser(actor)).with(xsrf()));
    }

    @Test
    void participantListIsOrderedByRoleAndOnlyVisibleToParticipants() throws Exception {
        TestUser ana = users.active();
        TestUser co = users.active();
        TestUser guest = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Festa", "PUBLIC");
        addParticipant(jdbc, eventId, guest, "PARTICIPANT");
        addParticipant(jdbc, eventId, co, "CO_ORGANIZER");

        mvc.perform(get("/api/v1/events/{id}/participants", eventId).with(asUser(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.items[0].role").value("ORGANIZER"))
                .andExpect(jsonPath("$.items[0].person.id").value(ana.id().toString()))
                .andExpect(jsonPath("$.items[1].role").value("CO_ORGANIZER"))
                .andExpect(jsonPath("$.items[2].role").value("PARTICIPANT"));

        // Evento público: quem não participa vê o evento, mas não a lista.
        mvc.perform(get("/api/v1/events/{id}/participants", eventId).with(asUser(users.active())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVENT_PARTICIPANTS_ONLY"));
        mvc.perform(get("/api/v1/events/{id}/participants", eventId)).andExpect(status().isUnauthorized());
    }

    @Test
    void participantsCanChangeTheirAttendanceAndEveryoneSeesTheSummary() throws Exception {
        TestUser ana = users.active();
        TestUser bia = users.active();
        TestUser carla = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Festa presencial", "PRIVATE");
        addParticipant(jdbc, eventId, bia, "PARTICIPANT");
        addParticipant(jdbc, eventId, carla, "PARTICIPANT");

        mvc.perform(patch("/api/v1/events/{id}/participants/me/attendance", eventId)
                        .with(asUser(bia)).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attendanceStatus\":\"NOT_ATTENDING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceStatus").value("NOT_ATTENDING"));

        mvc.perform(get("/api/v1/events/{id}/participants/attendance-summary", eventId).with(asUser(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attending").value(1))
                .andExpect(jsonPath("$.notAttending").value(1))
                .andExpect(jsonPath("$.unanswered").value(1))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.myStatus").value("ATTENDING"));

        mvc.perform(get("/api/v1/events/{id}/participants", eventId).with(asUser(carla)))
                .andExpect(jsonPath("$.items[1].attendanceStatus").value("NOT_ATTENDING"))
                .andExpect(jsonPath("$.items[2].attendanceStatus").doesNotExist());
    }

    @Test
    void privateEventParticipantListIsNotFoundForStrangers() throws Exception {
        UUID eventId = EventApi.create(mvc, users.active(), "Privado", "PRIVATE");

        mvc.perform(get("/api/v1/events/{id}/participants", eventId).with(asUser(users.active())))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyTheOrganizerPromotesAndDemotes() throws Exception {
        TestUser ana = users.active();
        TestUser bia = users.active();
        TestUser carla = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Bodas", "PRIVATE");
        addParticipant(jdbc, eventId, bia, "PARTICIPANT");
        addParticipant(jdbc, eventId, carla, "PARTICIPANT");

        changeRole(ana, eventId, bia, "CO_ORGANIZER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CO_ORGANIZER"));

        // Coorganizador não promove ninguém.
        changeRole(bia, eventId, carla, "CO_ORGANIZER")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVENT_ORGANIZER_ONLY"));

        // O título de organizador não é transferido.
        changeRole(ana, eventId, bia, "ORGANIZER")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORGANIZER_IS_PERMANENT"));
        changeRole(ana, eventId, ana, "PARTICIPANT")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORGANIZER_IS_PERMANENT"));

        changeRole(ana, eventId, bia, "PARTICIPANT").andExpect(jsonPath("$.role").value("PARTICIPANT"));
        changeRole(ana, eventId, users.active(), "CO_ORGANIZER")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PARTICIPANT_NOT_FOUND"));
    }

    @Test
    void removalRulesFollowTheRoles() throws Exception {
        TestUser ana = users.active();
        TestUser co = users.active();
        TestUser co2 = users.active();
        TestUser guest = users.active();
        TestUser guest2 = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Batizado", "PRIVATE");
        addParticipant(jdbc, eventId, co, "CO_ORGANIZER");
        addParticipant(jdbc, eventId, co2, "CO_ORGANIZER");
        addParticipant(jdbc, eventId, guest, "PARTICIPANT");
        addParticipant(jdbc, eventId, guest2, "PARTICIPANT");

        remove(guest, eventId, guest2).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVENT_ORGANIZATION_ONLY"));
        remove(co, eventId, co2).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVENT_ORGANIZER_ONLY"));
        remove(co, eventId, ana).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORGANIZER_IS_PERMANENT"));
        remove(ana, eventId, ana).andExpect(status().isUnprocessableEntity());

        remove(co, eventId, guest).andExpect(status().isNoContent());
        remove(ana, eventId, co2).andExpect(status().isNoContent());
        remove(guest2, eventId, guest2).andExpect(status().isNoContent());

        // Removidos deixam de ver o evento privado.
        mvc.perform(get("/api/v1/events/{id}", eventId).with(asUser(guest))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/events/{id}", eventId).with(asUser(guest2))).andExpect(status().isNotFound());
    }

    @Test
    void privateProfileIsHiddenFromPeopleWithoutAnEventInCommon() throws Exception {
        TestUser ana = users.active();
        jdbc.update("update app_user set profile_privacy = 'PRIVATE' where id = ?", ana.id());
        TestUser friend = users.active();
        TestUser stranger = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Show na praça", "PUBLIC");
        addParticipant(jdbc, eventId, friend, "PARTICIPANT");

        mvc.perform(get("/api/v1/events/{id}", eventId).with(asUser(stranger)))
                .andExpect(jsonPath("$.organizer.id").value(ana.id().toString()))
                .andExpect(jsonPath("$.organizer.profileHidden").value(true))
                .andExpect(jsonPath("$.organizer.displayName").doesNotExist())
                .andExpect(jsonPath("$.organizer.username").doesNotExist())
                .andExpect(jsonPath("$.organizer.photoUrl").doesNotExist());
        mvc.perform(get("/api/v1/events/{id}", eventId))
                .andExpect(jsonPath("$.organizer.profileHidden").value(true));

        mvc.perform(get("/api/v1/events/{id}", eventId).with(asUser(friend)))
                .andExpect(jsonPath("$.organizer.profileHidden").value(false))
                .andExpect(jsonPath("$.organizer.username").value(ana.username()));
        mvc.perform(get("/api/v1/events/{id}", eventId).with(asUser(ana)))
                .andExpect(jsonPath("$.organizer.profileHidden").value(false));
    }
}
