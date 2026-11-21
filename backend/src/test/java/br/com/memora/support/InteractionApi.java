package br.com.memora.support;

import static br.com.memora.support.WebSecuritySupport.asUser;
import static br.com.memora.support.WebSecuritySupport.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.memora.support.TestUsers.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Atalhos para comentários e reações nos testes, pela API pública. {@code user} nulo = visitante anônimo. */
public final class InteractionApi {

    private InteractionApi() {
    }

    public static ResultActions comment(MockMvc mvc, TestUser user, UUID memoryId, String json) throws Exception {
        return mvc.perform(post("/api/v1/memories/{id}/comments", memoryId).with(asUser(user)).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public static UUID commentText(MockMvc mvc, TestUser user, UUID memoryId, String text, String visibility)
            throws Exception {
        return idOf(comment(mvc, user, memoryId, body(text, visibility)));
    }

    public static ResultActions reply(MockMvc mvc, TestUser user, UUID commentId, String json) throws Exception {
        return mvc.perform(post("/api/v1/comments/{id}/replies", commentId).with(asUser(user)).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public static UUID replyText(MockMvc mvc, TestUser user, UUID commentId, String text, String visibility)
            throws Exception {
        return idOf(reply(mvc, user, commentId, body(text, visibility)));
    }

    public static ResultActions patchComment(MockMvc mvc, TestUser user, UUID commentId, String json)
            throws Exception {
        return mvc.perform(patch("/api/v1/comments/{id}", commentId).with(asUser(user)).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public static ResultActions deleteComment(MockMvc mvc, TestUser user, UUID commentId) throws Exception {
        return mvc.perform(delete("/api/v1/comments/{id}", commentId).with(asUser(user)).with(xsrf()));
    }

    public static ResultActions getComment(MockMvc mvc, TestUser user, UUID commentId) throws Exception {
        return mvc.perform(as(get("/api/v1/comments/{id}", commentId), user));
    }

    public static ResultActions listComments(MockMvc mvc, TestUser user, UUID memoryId) throws Exception {
        return mvc.perform(as(get("/api/v1/memories/{id}/comments", memoryId), user));
    }

    public static ResultActions listReplies(MockMvc mvc, TestUser user, UUID commentId) throws Exception {
        return mvc.perform(as(get("/api/v1/comments/{id}/replies", commentId), user));
    }

    public static ResultActions getMemory(MockMvc mvc, TestUser user, UUID memoryId) throws Exception {
        return mvc.perform(as(get("/api/v1/memories/{id}", memoryId), user));
    }

    public static ResultActions react(MockMvc mvc, TestUser user, UUID memoryId, String emoji) throws Exception {
        return mvc.perform(put("/api/v1/memories/{id}/reactions", memoryId).with(asUser(user)).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"emoji\":\"" + emoji + "\"}"));
    }

    public static ResultActions unreact(MockMvc mvc, TestUser user, UUID memoryId) throws Exception {
        return mvc.perform(delete("/api/v1/memories/{id}/reactions", memoryId).with(asUser(user)).with(xsrf()));
    }

    public static ResultActions reactionSummary(MockMvc mvc, TestUser user, UUID memoryId) throws Exception {
        return mvc.perform(as(get("/api/v1/memories/{id}/reactions/summary", memoryId), user));
    }

    public static ResultActions changeMural(MockMvc mvc, TestUser organizer, UUID eventId, String status)
            throws Exception {
        return mvc.perform(patch("/api/v1/events/{id}/mural", eventId).with(asUser(organizer)).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
    }

    public static ResultActions patchMemory(MockMvc mvc, TestUser user, UUID memoryId, String json) throws Exception {
        return mvc.perform(patch("/api/v1/memories/{id}", memoryId).with(asUser(user)).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, TestUser user) {
        return user == null ? request : request.with(asUser(user));
    }

    private static String body(String text, String visibility) {
        return visibility == null ? "{\"text\":\"%s\"}".formatted(text)
                : "{\"text\":\"%s\",\"visibility\":\"%s\"}".formatted(text, visibility);
    }

    private static UUID idOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }
}
