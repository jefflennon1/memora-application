package br.com.memora.event;

import static br.com.memora.support.EventApi.addParticipant;
import static br.com.memora.support.EventApi.future;
import static br.com.memora.support.EventApi.past;
import static br.com.memora.support.WebSecuritySupport.asUser;
import static br.com.memora.support.WebSecuritySupport.xsrf;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.memora.support.EventApi;
import br.com.memora.support.IntegrationTest;
import br.com.memora.support.TestUsers;
import br.com.memora.support.TestUsers.TestUser;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Entrega 3 — criação, edição e mural de eventos.
 */
@IntegrationTest
class EventsIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions createEvent(TestUser user, String json) throws Exception {
        return mvc.perform(post("/api/v1/events").with(asUser(user)).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions patchEvent(TestUser user, UUID eventId, String json) throws Exception {
        return mvc.perform(patch("/api/v1/events/{id}", eventId).with(asUser(user)).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions changeMural(TestUser user, UUID eventId, String status) throws Exception {
        return mvc.perform(patch("/api/v1/events/{id}/mural", eventId).with(asUser(user)).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
    }

    // ------------------------------------------------------------------ criação

    @Test
    void creatorBecomesOrganizerAndMuralStartsOpen() throws Exception {
        TestUser ana = users.active();

        createEvent(ana, """
                {"name":"  Chá de Fraldas do Liam ","description":"Uma tarde para celebrar.",
                 "categoryCode":"BABY_SHOWER","startDate":"%s","startTime":"15:00","endTime":"18:00",
                 "modality":"IN_PERSON","city":"Belo Horizonte","address":"Rua das Flores, 10","privacy":"PUBLIC"}
                """.formatted(future()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.matchesPattern("/api/v1/events/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.name").value("Chá de Fraldas do Liam"))
                .andExpect(jsonPath("$.category.code").value("BABY_SHOWER"))
                .andExpect(jsonPath("$.category.name").value("Chá de bebê / Chá de fraldas"))
                .andExpect(jsonPath("$.startDate").value(future().toString()))
                .andExpect(jsonPath("$.startTime").value("15:00"))
                .andExpect(jsonPath("$.endTime").value("18:00"))
                .andExpect(jsonPath("$.city").value("Belo Horizonte"))
                .andExpect(jsonPath("$.privacy").value("PUBLIC"))
                .andExpect(jsonPath("$.muralStatus").value("OPEN"))
                .andExpect(jsonPath("$.myRole").value("ORGANIZER"))
                .andExpect(jsonPath("$.organizer.id").value(ana.id().toString()))
                .andExpect(jsonPath("$.organizer.username").value(ana.username()));
    }

    @Test
    void eventIsPrivateByDefaultAndOnlineEventsHaveNoAddress() throws Exception {
        createEvent(users.active(), """
                {"name":"Aniversário online","startDate":"%s","modality":"ONLINE","city":"Recife","address":"Rua X"}
                """.formatted(future()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.privacy").value("PRIVATE"))
                .andExpect(jsonPath("$.modality").value("ONLINE"))
                .andExpect(jsonPath("$.city").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.category").doesNotExist());
    }

    @Test
    void todayIsAcceptedButThePastIsNot() throws Exception {
        TestUser user = users.active();

        createEvent(user, """
                {"name":"Hoje","startDate":"%s","modality":"NO_LOCATION"}
                """.formatted(LocalDate.now(ZoneOffset.UTC)))
                .andExpect(status().isCreated());

        createEvent(user, """
                {"name":"Ontem","startDate":"%s","modality":"NO_LOCATION"}
                """.formatted(past()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EVENT_START_IN_PAST"));
    }

    @Test
    void endCannotPrecedeStart() throws Exception {
        TestUser user = users.active();

        createEvent(user, """
                {"name":"X","startDate":"%s","endDate":"%s","modality":"NO_LOCATION"}
                """.formatted(future(), future().minusDays(1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EVENT_END_BEFORE_START"));

        createEvent(user, """
                {"name":"X","startDate":"%s","startTime":"18:00","endTime":"15:00","modality":"NO_LOCATION"}
                """.formatted(future()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EVENT_END_BEFORE_START"));
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        TestUser user = users.active();

        createEvent(user, "{\"name\":\"   \",\"startDate\":\"%s\",\"modality\":\"ONLINE\"}".formatted(future()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));

        createEvent(user, "{\"name\":\"X\",\"modality\":\"ONLINE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("startDate"));

        createEvent(user, """
                {"name":"X","startDate":"%s","modality":"ONLINE","categoryCode":"NOPE"}
                """.formatted(future()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT_CATEGORY"));

        createEvent(user, "{\"name\":\"X\",\"startDate\":\"%s\",\"modality\":\"BOAT\"}".formatted(future()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        createEvent(user, "{\"name\":\"X\",\"startDate\":\"05/12/2026\",\"modality\":\"ONLINE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void creatingRequiresSessionCompletedOnboardingAndCsrf() throws Exception {
        String json = "{\"name\":\"X\",\"startDate\":\"%s\",\"modality\":\"ONLINE\"}".formatted(future());

        mvc.perform(post("/api/v1/events").with(xsrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnauthorized());

        createEvent(users.pending(), json)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ONBOARDING_REQUIRED"));

        mvc.perform(post("/api/v1/events").with(asUser(users.active()))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ edição

    @Test
    void organizerEditsOnlyWhatWasSentAndCanClearOptionalFields() throws Exception {
        TestUser ana = users.active();
        UUID id = EventApi.create(mvc, ana, """
                {"name":"Casamento","description":"Texto","categoryCode":"WEDDING","startDate":"%s",
                 "startTime":"16:00","modality":"IN_PERSON","city":"Ouro Preto","address":"Igreja"}
                """.formatted(future()));

        patchEvent(ana, id, "{\"name\":\"Casamento de Ana e Bia\",\"description\":null,\"categoryCode\":null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Casamento de Ana e Bia"))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.category").doesNotExist())
                // Campos ausentes continuam como estavam.
                .andExpect(jsonPath("$.startTime").value("16:00"))
                .andExpect(jsonPath("$.city").value("Ouro Preto"))
                .andExpect(jsonPath("$.address").value("Igreja"));

        // Virar online apaga o local.
        patchEvent(ana, id, "{\"modality\":\"ONLINE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist());

        // null explícito apaga o horário; segundos enviados são descartados.
        patchEvent(ana, id, "{\"startTime\":null,\"endTime\":\"20:30:45\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").doesNotExist())
                .andExpect(jsonPath("$.endTime").value("20:30"))
                .andExpect(jsonPath("$.name").value("Casamento de Ana e Bia"));
    }

    @Test
    void editingRevalidatesDates() throws Exception {
        TestUser ana = users.active();
        UUID id = EventApi.create(mvc, ana, "Festa", "PRIVATE");

        patchEvent(ana, id, "{\"startDate\":\"%s\"}".formatted(past()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EVENT_START_IN_PAST"));

        patchEvent(ana, id, "{\"endDate\":\"%s\"}".formatted(future().minusDays(1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EVENT_END_BEFORE_START"));

        patchEvent(ana, id, "{\"name\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT_NAME"));
    }

    @Test
    void anEventThatAlreadyHappenedRemainsEditable() throws Exception {
        TestUser ana = users.active();
        UUID id = EventApi.create(mvc, ana, "Viagem", "PRIVATE");
        jdbc.update("update event set start_date = ? where id = ?", past(), id);

        patchEvent(ana, id, "{\"description\":\"Que viagem!\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Que viagem!"))
                .andExpect(jsonPath("$.startDate").value(past().toString()));
    }

    @Test
    void coOrganizerEditsButCannotChangePrivacyOrMural() throws Exception {
        TestUser ana = users.active();
        TestUser bia = users.active();
        UUID id = EventApi.create(mvc, ana, "Bodas", "PRIVATE");
        addParticipant(jdbc, id, bia, "CO_ORGANIZER");

        patchEvent(bia, id, "{\"description\":\"Editado pela Bia\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("CO_ORGANIZER"));

        patchEvent(bia, id, "{\"privacy\":\"PUBLIC\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVENT_ORGANIZER_ONLY"));

        changeMural(bia, id, "CLOSED")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVENT_ORGANIZER_ONLY"));
    }

    @Test
    void organizerChangesPrivacy() throws Exception {
        TestUser ana = users.active();
        UUID id = EventApi.create(mvc, ana, "Formatura", "PRIVATE");

        patchEvent(ana, id, "{\"privacy\":\"PUBLIC\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privacy").value("PUBLIC"));

        mvc.perform(get("/api/v1/events/{id}", id)).andExpect(status().isOk());
    }

    @Test
    void participantsAndStrangersCannotEdit() throws Exception {
        TestUser ana = users.active();
        TestUser guest = users.active();
        TestUser stranger = users.active();
        UUID privateEvent = EventApi.create(mvc, ana, "Privado", "PRIVATE");
        UUID publicEvent = EventApi.create(mvc, ana, "Público", "PUBLIC");
        addParticipant(jdbc, privateEvent, guest, "PARTICIPANT");

        patchEvent(guest, privateEvent, "{\"name\":\"Hack\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVENT_ORGANIZATION_ONLY"));

        // Evento público: quem não participa vê, mas não edita.
        patchEvent(stranger, publicEvent, "{\"name\":\"Hack\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVENT_ORGANIZATION_ONLY"));

        // Evento privado: quem não participa nem fica sabendo que ele existe.
        patchEvent(stranger, privateEvent, "{\"name\":\"Hack\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        changeMural(stranger, privateEvent, "CLOSED")
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/events/{id}", privateEvent).with(asUser(ana)))
                .andExpect(jsonPath("$.name").value("Privado"));
    }

    // ------------------------------------------------------------------ mural

    @Test
    void organizerClosesAndReopensTheMural() throws Exception {
        TestUser ana = users.active();
        UUID id = EventApi.create(mvc, ana, "Reencontro", "PRIVATE");

        changeMural(ana, id, "CLOSED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muralStatus").value("CLOSED"))
                .andExpect(jsonPath("$.muralStatusChangedAt").exists());

        // Pedir o estado atual é idempotente.
        changeMural(ana, id, "CLOSED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muralStatus").value("CLOSED"));

        changeMural(ana, id, "OPEN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.muralStatus").value("OPEN"));
    }

    @Test
    void muralDoesNotCloseByItselfAfterTheEvent() throws Exception {
        TestUser ana = users.active();
        UUID id = EventApi.create(mvc, ana, "Já passou", "PRIVATE");
        jdbc.update("update event set start_date = ? where id = ?", past(), id);

        mvc.perform(get("/api/v1/events/{id}", id).with(asUser(ana)))
                .andExpect(jsonPath("$.muralStatus").value("OPEN"));
    }

    @Test
    void participantCannotChangeTheMural() throws Exception {
        TestUser ana = users.active();
        TestUser guest = users.active();
        UUID id = EventApi.create(mvc, ana, "Batizado", "PRIVATE");
        addParticipant(jdbc, id, guest, "PARTICIPANT");

        changeMural(guest, id, "CLOSED")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVENT_ORGANIZER_ONLY"));

        changeMural(ana, id, "SOMETIMES")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unknownEventIsNotFound() throws Exception {
        patchEvent(users.active(), UUID.randomUUID(), "{\"name\":\"X\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void organizerIsPermanentAndUnique() throws Exception {
        TestUser ana = users.active();
        UUID id = EventApi.create(mvc, ana, "Único", "PRIVATE");

        assertThatThrownBy(() -> addParticipant(jdbc, id, users.active(), "ORGANIZER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
