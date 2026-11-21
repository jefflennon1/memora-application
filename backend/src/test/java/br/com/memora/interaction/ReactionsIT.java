package br.com.memora.interaction;

import static br.com.memora.support.EventApi.addParticipant;
import static br.com.memora.support.InteractionApi.changeMural;
import static br.com.memora.support.InteractionApi.getMemory;
import static br.com.memora.support.InteractionApi.react;
import static br.com.memora.support.InteractionApi.reactionSummary;
import static br.com.memora.support.InteractionApi.unreact;
import static br.com.memora.support.MemoryApi.publishText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.memora.support.EventApi;
import br.com.memora.support.IntegrationTest;
import br.com.memora.support.TestUsers;
import br.com.memora.support.TestUsers.TestUser;
import br.com.memora.support.WebSecuritySupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Entrega 6 — reações por emoji: uma por pessoa, contagem por emoji, nunca quem reagiu.
 */
@IntegrationTest
class ReactionsIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void oneReactionPerPersonCountedByEmojiWithoutRevealingWhoReacted() throws Exception {
        TestUser ana = users.active();
        TestUser bia = users.active();
        TestUser caio = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Aniversário", "PRIVATE");
        addParticipant(jdbc, eventId, bia, "PARTICIPANT");
        addParticipant(jdbc, eventId, caio, "PARTICIPANT");
        UUID memoryId = publishText(mvc, ana, eventId, "Parabéns!", null);

        react(mvc, ana, memoryId, "🎉").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.mine").value("🎉"));
        react(mvc, bia, memoryId, "❤").andExpect(status().isOk());
        String body = react(mvc, caio, memoryId, "❤️")
                .andExpect(status().isOk())
                // "❤" e "❤️" contam como o mesmo emoji; o mais usado vem primeiro.
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].emoji").value("❤️"))
                .andExpect(jsonPath("$.items[0].count").value(2))
                .andExpect(jsonPath("$.items[1].emoji").value("🎉"))
                .andExpect(jsonPath("$.items[1].count").value(1))
                .andExpect(jsonPath("$.mine").value("❤️"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(ana.id().toString(), bia.id().toString(), ana.username(), bia.username());

        // Reagir de novo troca a reação; não soma.
        react(mvc, caio, memoryId, "🎉")
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[0].emoji").value("🎉"))
                .andExpect(jsonPath("$.items[0].count").value(2))
                .andExpect(jsonPath("$.mine").value("🎉"));
        react(mvc, caio, memoryId, "🎉").andExpect(jsonPath("$.total").value(3));

        reactionSummary(mvc, bia, memoryId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mine").value("❤️"));
        getMemory(mvc, bia, memoryId)
                .andExpect(jsonPath("$.reactions.total").value(3))
                .andExpect(jsonPath("$.reactions.mine").value("❤️"));

        unreact(mvc, bia, memoryId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.mine").doesNotExist());
        unreact(mvc, bia, memoryId).andExpect(status().isOk());
    }

    @Test
    void theMuralShowsReactionsAndCommentCountsForEachMemory() throws Exception {
        TestUser ana = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Mural", "PRIVATE");
        UUID first = publishText(mvc, ana, eventId, "Primeira", null);
        publishText(mvc, ana, eventId, "Segunda", null);
        react(mvc, ana, first, "😂").andExpect(status().isOk());

        mvc.perform(get("/api/v1/events/{id}/memories", eventId).with(WebSecuritySupport.asUser(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reactions.total").value(1))
                .andExpect(jsonPath("$.items[0].reactions.items[0].emoji").value("😂"))
                .andExpect(jsonPath("$.items[0].reactions.mine").value("😂"))
                .andExpect(jsonPath("$.items[0].commentCount").value(0))
                .andExpect(jsonPath("$.items[1].reactions.total").value(0))
                .andExpect(jsonPath("$.items[1].reactions.items.length()").value(0));
    }

    @Test
    void onlyOneEmojiIsAccepted() throws Exception {
        TestUser ana = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Emojis", "PRIVATE");
        UUID memoryId = publishText(mvc, ana, eventId, "Oi", null);

        for (String invalid : new String[] {"", "ok", "😀😀", "1"}) {
            react(mvc, ana, memoryId, invalid)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_EMOJI"));
        }
        react(mvc, ana, memoryId, "👍🏽").andExpect(status().isOk());
        react(mvc, ana, memoryId, "👨‍👩‍👧").andExpect(jsonPath("$.mine").value("👨‍👩‍👧"));
    }

    @Test
    void visitorsSeeTheSummaryOfPublicMemoriesButOnlyParticipantsReact() throws Exception {
        TestUser ana = users.active();
        TestUser stranger = users.active();
        UUID publicEvent = EventApi.create(mvc, ana, "Aberto", "PUBLIC");
        UUID publicMemory = publishText(mvc, ana, publicEvent, "Para todos", "PUBLIC");
        UUID restrictedMemory = publishText(mvc, ana, publicEvent, "Só nós", null);
        react(mvc, ana, publicMemory, "🥳").andExpect(status().isOk());

        reactionSummary(mvc, null, publicMemory)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.mine").doesNotExist());
        reactionSummary(mvc, stranger, publicMemory).andExpect(status().isOk());
        reactionSummary(mvc, stranger, restrictedMemory)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMORY_NOT_FOUND"));

        react(mvc, stranger, publicMemory, "🥳")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVENT_PARTICIPANTS_ONLY"));
        mvc.perform(put("/api/v1/memories/{id}/reactions", publicMemory).with(WebSecuritySupport.xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"emoji\":\"🥳\"}"))
                .andExpect(status().isUnauthorized());

        UUID privateEvent = EventApi.create(mvc, ana, "Fechado", "PRIVATE");
        UUID privateMemory = publishText(mvc, ana, privateEvent, "Nossa", null);
        reactionSummary(mvc, null, privateMemory).andExpect(status().isNotFound());
        react(mvc, stranger, privateMemory, "🥳").andExpect(status().isNotFound());
    }

    @Test
    void aClosedMuralAcceptsNoNewReactionsButYouCanRemoveYours() throws Exception {
        TestUser ana = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Encerrado", "PRIVATE");
        UUID memoryId = publishText(mvc, ana, eventId, "Fim", null);
        react(mvc, ana, memoryId, "😢").andExpect(status().isOk());

        changeMural(mvc, ana, eventId, "CLOSED").andExpect(status().isOk());
        react(mvc, ana, memoryId, "🙂")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MURAL_CLOSED"));
        unreact(mvc, ana, memoryId).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void reactionsGoAwayWithTheMemory() throws Exception {
        TestUser ana = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Apagar", "PRIVATE");
        UUID memoryId = publishText(mvc, ana, eventId, "Some", null);
        react(mvc, ana, memoryId, "👋").andExpect(status().isOk());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/memories/{id}", memoryId)
                        .with(WebSecuritySupport.asUser(ana)).with(WebSecuritySupport.xsrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from reaction where memory_id = ?", Integer.class, memoryId))
                .isZero();
    }
}
