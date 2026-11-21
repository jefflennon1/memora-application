package br.com.memora.profile;

import static br.com.memora.support.EventApi.addParticipant;
import static br.com.memora.support.WebSecuritySupport.asUser;
import static br.com.memora.support.WebSecuritySupport.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.memora.identity.domain.ProfilePrivacy;
import br.com.memora.profile.application.ProfileService;
import br.com.memora.support.EventApi;
import br.com.memora.support.InMemoryObjectStorage;
import br.com.memora.support.IntegrationTest;
import br.com.memora.support.TestImages;
import br.com.memora.support.TestUsers;
import br.com.memora.support.TestUsers.TestUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ManagedImagesIT {

    @Autowired MockMvc mvc;
    @Autowired TestUsers users;
    @Autowired JdbcTemplate jdbc;
    @Autowired InMemoryObjectStorage storage;
    @Autowired ProfileService profiles;

    @Test
    void profilePhotoCanBeInsertedViewedReplacedAndDeleted() throws Exception {
        TestUser ana = users.active();
        byte[] first = TestImages.png(320, 320);

        upload("/api/v1/users/me/photo", ana, first)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl", startsWith("/api/v1/users/" + ana.id() + "/photo?v=")));
        String firstKey = jdbc.queryForObject("select photo_storage_key from app_user where id = ?",
                String.class, ana.id());
        assertThat(storage.contains(firstKey)).isTrue();
        mvc.perform(get("/api/v1/users/{id}/photo", ana.id()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(first));

        byte[] replacement = TestImages.jpeg(480, 480);
        upload("/api/v1/users/me/photo", ana, replacement).andExpect(status().isOk());
        String replacementKey = jdbc.queryForObject("select photo_storage_key from app_user where id = ?",
                String.class, ana.id());
        assertThat(replacementKey).isNotEqualTo(firstKey);
        assertThat(storage.contains(firstKey)).isFalse();
        assertThat(storage.contains(replacementKey)).isTrue();

        mvc.perform(delete("/api/v1/users/me/photo").with(asUser(ana)).with(xsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").doesNotExist());
        assertThat(storage.contains(replacementKey)).isFalse();
    }

    @Test
    void privateProfilePhotoIsOnlyVisibleToAllowedPeople() throws Exception {
        TestUser ana = users.active();
        profiles.update(ana.id(), new ProfileService.ProfileUpdateCommand(null, null, false, ProfilePrivacy.PRIVATE));
        upload("/api/v1/users/me/photo", ana, TestImages.png(80, 80)).andExpect(status().isOk());

        mvc.perform(get("/api/v1/users/{id}/photo", ana.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_PHOTO_NOT_FOUND"));
        mvc.perform(get("/api/v1/users/{id}/photo", ana.id()).with(asUser(ana)))
                .andExpect(status().isOk());
    }

    @Test
    void eventOrganizationManagesCoverAndVisibilityFollowsTheEvent() throws Exception {
        TestUser organizer = users.active();
        TestUser participant = users.active();
        UUID eventId = EventApi.create(mvc, organizer, "Encontro com capa", "PUBLIC");
        addParticipant(jdbc, eventId, participant, "PARTICIPANT");
        byte[] cover = TestImages.jpeg(1200, 500);

        upload("/api/v1/events/" + eventId + "/cover", organizer, cover)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverUrl", startsWith("/api/v1/events/" + eventId + "/cover?v=")));
        String key = jdbc.queryForObject("select cover_storage_key from event where id = ?", String.class, eventId);
        assertThat(storage.contains(key)).isTrue();
        mvc.perform(get("/api/v1/events/{id}/cover", eventId))
                .andExpect(status().isOk())
                .andExpect(content().bytes(cover));

        upload("/api/v1/events/" + eventId + "/cover", participant, TestImages.png(20, 20))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVENT_ORGANIZATION_ONLY"));

        mvc.perform(delete("/api/v1/events/{id}/cover", eventId).with(asUser(organizer)).with(xsrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverUrl").doesNotExist());
        assertThat(storage.contains(key)).isFalse();
    }

    @Test
    void privateEventCoverDoesNotLeakToVisitors() throws Exception {
        TestUser organizer = users.active();
        UUID eventId = EventApi.create(mvc, organizer, "Capa privada", "PRIVATE");
        upload("/api/v1/events/" + eventId + "/cover", organizer, TestImages.png(100, 50))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/events/{id}/cover", eventId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        mvc.perform(get("/api/v1/events/{id}/cover", eventId).with(asUser(organizer)))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions upload(String path, TestUser user, byte[] bytes)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "foto.png", "image/png", bytes);
        return mvc.perform(multipart(HttpMethod.PUT, path).file(file).with(asUser(user)).with(xsrf()));
    }
}
