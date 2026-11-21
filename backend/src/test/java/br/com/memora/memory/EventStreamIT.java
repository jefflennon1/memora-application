package br.com.memora.memory;

import static br.com.memora.support.EventApi.addParticipant;
import static br.com.memora.support.MemoryApi.publishText;
import static br.com.memora.support.WebSecuritySupport.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.memora.support.EventApi;
import br.com.memora.support.IntegrationTest;
import br.com.memora.support.TestUsers;
import br.com.memora.support.TestUsers.TestUser;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
class EventStreamIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void requiresAnAuthenticatedParticipant() throws Exception {
        TestUser organizer = users.active();
        TestUser outsider = users.active();
        UUID eventId = EventApi.create(mvc, organizer, "Stream privado", "PRIVATE");

        mvc.perform(get("/api/v1/events/{id}/stream", eventId).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/events/{id}/stream", eventId).with(asUser(outsider))
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void participantReceivesMinimalNoticeOnlyAfterAnotherPersonPublishes() throws Exception {
        TestUser organizer = users.active();
        TestUser participant = users.active();
        UUID eventId = EventApi.create(mvc, organizer, "Stream do mural", "PRIVATE");
        addParticipant(jdbc, eventId, participant, "PARTICIPANT");

        MvcResult stream = mvc.perform(get("/api/v1/events/{id}/stream", eventId).with(asUser(participant))
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();
        stream.getResponse().setCharacterEncoding(StandardCharsets.UTF_8.name());

        UUID memoryId = publishText(mvc, organizer, eventId, "Nova memoria", null);
        String body = awaitEvent(stream, "event:memory-created");

        assertThat(body).contains("event:ready")
                .contains("event:memory-created")
                .contains(memoryId.toString())
                .contains(eventId.toString())
                .doesNotContain("Nova memoria");
        stream.getRequest().getAsyncContext().complete();
    }

    private static String awaitEvent(MvcResult stream, String expected) throws Exception {
        for (int attempt = 0; attempt < 50; attempt++) {
            String body = stream.getResponse().getContentAsString(StandardCharsets.UTF_8);
            if (body.contains(expected)) {
                return body;
            }
            Thread.sleep(20);
        }
        return stream.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
