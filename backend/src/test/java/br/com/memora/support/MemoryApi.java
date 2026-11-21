package br.com.memora.support;

import static br.com.memora.support.WebSecuritySupport.asUser;
import static br.com.memora.support.WebSecuritySupport.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.memora.support.TestUsers.TestUser;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Atalhos para enviar imagens e publicar memórias nos testes, pela API pública. */
public final class MemoryApi {

    private MemoryApi() {
    }

    public static ResultActions upload(MockMvc mvc, TestUser user, UUID eventId, byte[] content, String filename,
                                       String declaredType) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, declaredType, content);
        return mvc.perform(multipart("/api/v1/events/{id}/media", eventId).file(file).with(asUser(user)).with(xsrf()));
    }

    public static UUID uploadImage(MockMvc mvc, TestUser user, UUID eventId) throws Exception {
        String body = upload(mvc, user, eventId, TestImages.png(40, 30), "foto.png", "image/png")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    public static UUID uploadAudio(MockMvc mvc, TestUser user, UUID eventId, int seconds) throws Exception {
        String body = upload(mvc, user, eventId, TestAudio.wav(seconds), "audio.wav", "audio/wav")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    public static ResultActions publish(MockMvc mvc, TestUser user, UUID eventId, String json) throws Exception {
        return mvc.perform(post("/api/v1/events/{id}/memories", eventId).with(asUser(user)).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    public static UUID publishText(MockMvc mvc, TestUser user, UUID eventId, String text, String visibility)
            throws Exception {
        String json = visibility == null ? "{\"text\":\"%s\"}".formatted(text)
                : "{\"text\":\"%s\",\"visibility\":\"%s\"}".formatted(text, visibility);
        String body = publish(mvc, user, eventId, json).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }
}
