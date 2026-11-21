package br.com.memora.support;

import static br.com.memora.support.WebSecuritySupport.asUser;
import static br.com.memora.support.WebSecuritySupport.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.memora.support.TestUsers.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Atalhos para criar eventos e vínculos nos testes, sempre pela API pública quando possível. */
public final class EventApi {

    private EventApi() {
    }

    /** Uma data futura segura (independente do fuso de quem roda o teste). */
    public static LocalDate future() {
        return LocalDate.now(ZoneOffset.UTC).plusDays(30);
    }

    /** Uma data que é passado em qualquer fuso do planeta. */
    public static LocalDate past() {
        return LocalDate.now(ZoneOffset.UTC).minusDays(3);
    }

    public static UUID create(MockMvc mvc, TestUser organizer, String json) throws Exception {
        String body = mvc.perform(post("/api/v1/events").with(asUser(organizer)).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    public static UUID create(MockMvc mvc, TestUser organizer, String name, String privacy) throws Exception {
        return create(mvc, organizer, """
                {"name":"%s","startDate":"%s","modality":"IN_PERSON","city":"Belo Horizonte","privacy":"%s"}
                """.formatted(name, future(), privacy));
    }

    /** Vínculo direto no banco, enquanto convites e aprovação (Entrega 4) não existem. */
    public static void addParticipant(JdbcTemplate jdbc, UUID eventId, TestUser user, String role) {
        jdbc.update("insert into event_participant (id, event_id, user_id, role, joined_at) values (?, ?, ?, ?, now())",
                UUID.randomUUID(), eventId, user.id(), role);
    }

    /** Termo único por teste: o banco é compartilhado entre as classes de teste. */
    public static String uniqueToken() {
        return "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
