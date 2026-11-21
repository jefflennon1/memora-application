package br.com.memora.event;

import static br.com.memora.support.EventApi.addParticipant;
import static br.com.memora.support.EventApi.future;
import static br.com.memora.support.EventApi.uniqueToken;
import static br.com.memora.support.WebSecuritySupport.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.memora.support.EventApi;
import br.com.memora.support.IntegrationTest;
import br.com.memora.support.TestUsers;
import br.com.memora.support.TestUsers.TestUser;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Entrega 3 — quem vê o quê: consulta de evento, "meus eventos" e busca pública.
 * Nenhum dado (nem a existência) de evento privado pode vazar para quem não participa.
 */
@IntegrationTest
class EventVisibilityIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    // ------------------------------------------------------------------ consulta

    @Test
    void privateEventIsIndistinguishableFromANonexistentOne() throws Exception {
        TestUser ana = users.active();
        TestUser stranger = users.active();
        UUID privateEvent = EventApi.create(mvc, ana, "Segredo de família", "PRIVATE");

        String forStranger = mvc.perform(get("/api/v1/events/{id}", privateEvent).with(asUser(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"))
                .andReturn().getResponse().getContentAsString();
        String forMissing = mvc.perform(get("/api/v1/events/{id}", UUID.randomUUID()).with(asUser(stranger)))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // Mesma resposta, exceto o caminho pedido.
        assertThat(forStranger.replace(privateEvent.toString(), "ID")).isEqualTo(
                forMissing.replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "ID"));
        assertThat(forStranger).doesNotContain("Segredo");

        mvc.perform(get("/api/v1/events/{id}", privateEvent))
                .andExpect(status().isNotFound());
    }

    @Test
    void participantSeesPrivateEventWithTheirRole() throws Exception {
        TestUser ana = users.active();
        TestUser guest = users.active();
        UUID id = EventApi.create(mvc, ana, "Chá da Bia", "PRIVATE");
        addParticipant(jdbc, id, guest, "PARTICIPANT");

        mvc.perform(get("/api/v1/events/{id}", id).with(asUser(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Chá da Bia"))
                .andExpect(jsonPath("$.myRole").value("PARTICIPANT"));
    }

    @Test
    void publicEventIsVisibleToAnyoneWithoutRole() throws Exception {
        TestUser ana = users.active();
        UUID id = EventApi.create(mvc, ana, "Festa junina da rua", "PUBLIC");

        mvc.perform(get("/api/v1/events/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Festa junina da rua"))
                .andExpect(jsonPath("$.myRole").doesNotExist());

        mvc.perform(get("/api/v1/events/{id}", id).with(asUser(users.active())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").doesNotExist());

        // Quem ainda não concluiu o cadastro é tratado como visitante (sem bloqueio de onboarding).
        mvc.perform(get("/api/v1/events/{id}", id).with(asUser(users.pending())))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ meus eventos

    @Test
    void myEventsListsOnlyEventsIParticipateIn() throws Exception {
        TestUser ana = users.active();
        TestUser bia = users.active();
        UUID mine = EventApi.create(mvc, ana, "Da Ana", "PRIVATE");
        UUID invited = EventApi.create(mvc, bia, "Da Bia, com Ana", "PRIVATE");
        EventApi.create(mvc, bia, "Só da Bia", "PUBLIC");
        addParticipant(jdbc, invited, ana, "CO_ORGANIZER");

        mvc.perform(get("/api/v1/events/mine").with(asUser(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[*].id", Matchers.containsInAnyOrder(mine.toString(), invited.toString())))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].myRole".formatted(mine)).value("ORGANIZER"))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].myRole".formatted(invited)).value("CO_ORGANIZER"))
                .andExpect(jsonPath("$.items[?(@.id == '%s')].organizer.username".formatted(invited))
                        .value(bia.username()));
    }

    @Test
    void myEventsIsPaginatedAndRequiresSession() throws Exception {
        TestUser ana = users.active();
        for (int i = 0; i < 3; i++) {
            EventApi.create(mvc, ana, "Evento " + i, "PRIVATE");
        }

        mvc.perform(get("/api/v1/events/mine").param("size", "2").with(asUser(ana)))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));

        mvc.perform(get("/api/v1/events/mine").param("page", "1").param("size", "2").with(asUser(ana)))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));

        // Tamanho acima do máximo é limitado a 50.
        mvc.perform(get("/api/v1/events/mine").param("size", "500").with(asUser(ana)))
                .andExpect(jsonPath("$.size").value(50));

        mvc.perform(get("/api/v1/events/mine"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ busca pública

    @Test
    void publicSearchNeverShowsPrivateEvents() throws Exception {
        TestUser ana = users.active();
        String token = uniqueToken();
        UUID visible = EventApi.create(mvc, ana, "Aberto " + token, "PUBLIC");
        EventApi.create(mvc, ana, "Fechado " + token, "PRIVATE");

        // Nem para o próprio organizador o evento privado aparece na busca pública.
        mvc.perform(get("/api/v1/events/public").param("q", token).with(asUser(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(visible.toString()))
                .andExpect(jsonPath("$.items[0].myRole").value("ORGANIZER"));

        mvc.perform(get("/api/v1/events/public").param("q", "Fechado " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(content -> assertThat(content.getResponse().getContentAsString()).doesNotContain(token));
    }

    @Test
    void publicSearchIgnoresCaseAndAccents() throws Exception {
        TestUser ana = users.active();
        String token = uniqueToken();
        UUID id = EventApi.create(mvc, ana, "Chá de Fraldas do João " + token, "PUBLIC");

        mvc.perform(get("/api/v1/events/public").param("q", "CHA DE FRALDAS do joao " + token))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id.toString()));

        // Curingas do SQL digitados pelo usuário são tratados como texto.
        mvc.perform(get("/api/v1/events/public").param("q", "%" + token.substring(0, 3) + "_"))
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void publicSearchFiltersByCityAndCategory() throws Exception {
        TestUser ana = users.active();
        String token = uniqueToken();
        UUID wedding = EventApi.create(mvc, ana, """
                {"name":"Casamento %s","categoryCode":"WEDDING","startDate":"%s","modality":"IN_PERSON",
                 "city":"São Paulo","privacy":"PUBLIC"}
                """.formatted(token, future()));
        UUID birthday = EventApi.create(mvc, ana, """
                {"name":"Aniversário %s","categoryCode":"BIRTHDAY","startDate":"%s","modality":"IN_PERSON",
                 "city":"Belo Horizonte","privacy":"PUBLIC"}
                """.formatted(token, future()));

        mvc.perform(get("/api/v1/events/public").param("q", token).param("city", "sao paulo"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(wedding.toString()));

        mvc.perform(get("/api/v1/events/public").param("q", token).param("category", "BIRTHDAY"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(birthday.toString()));

        mvc.perform(get("/api/v1/events/public").param("q", token).param("category", "UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void privateEventTurnedPublicAppearsAndViceVersa() throws Exception {
        TestUser ana = users.active();
        String token = uniqueToken();
        UUID id = EventApi.create(mvc, ana, "Muda " + token, "PUBLIC");
        jdbc.update("update event set privacy = 'PRIVATE' where id = ?", id);

        mvc.perform(get("/api/v1/events/public").param("q", token))
                .andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/v1/events/{id}", id))
                .andExpect(status().isNotFound());
    }
}
