package br.com.memora.memory;

import static br.com.memora.support.EventApi.addParticipant;
import static br.com.memora.support.MemoryApi.publish;
import static br.com.memora.support.MemoryApi.upload;
import static br.com.memora.support.MemoryApi.uploadImage;
import static br.com.memora.support.MemoryApi.uploadAudio;
import static br.com.memora.support.WebSecuritySupport.asUser;
import static br.com.memora.support.WebSecuritySupport.xsrf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.memora.memory.application.MediaService;
import br.com.memora.support.EventApi;
import br.com.memora.support.InMemoryObjectStorage;
import br.com.memora.support.IntegrationTest;
import br.com.memora.support.TestImages;
import br.com.memora.support.TestVideo;
import br.com.memora.support.TestAudio;
import br.com.memora.support.TestUsers;
import br.com.memora.support.TestUsers.TestUser;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Entrega 5 — envio de imagens, validação pelo conteúdo e acesso controlado aos arquivos.
 */
@IntegrationTest
class MediaIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    InMemoryObjectStorage storage;

    @Autowired
    MediaService mediaService;

    private String storageKey(UUID mediaId) {
        return jdbc.queryForObject("select storage_key from media_file where id = ?", String.class, mediaId);
    }

    @Test
    void uploadDetectsTheRealTypeAndStoresTheFileOutsideTheDatabase() throws Exception {
        TestUser ana = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Fotos", "PRIVATE");
        byte[] jpeg = TestImages.jpegWithOrientation(640, 480, 6, true);

        // Nome e tipo declarados mentem; vale o conteúdo.
        String body = upload(mvc, ana, eventId, jpeg, "foto.png", "image/png")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.startsWith("/api/v1/media/")))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.width").value(480))
                .andExpect(jsonPath("$.height").value(640))
                .andExpect(jsonPath("$.sizeBytes").value(jpeg.length))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.id"));

        String key = storageKey(id);
        assertThat(key).isEqualTo("events/" + eventId + "/media/" + id).doesNotContain(ana.username());
        assertThat(storage.contains(key)).isTrue();

        mvc.perform(get("/api/v1/media/{id}", id).with(asUser(ana)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(jpeg))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, Matchers.containsString("private")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cross-Origin-Resource-Policy", "same-site"))
                .andExpect(header().string(HttpHeaders.ETAG, "\"" + id + "\""));

        mvc.perform(get("/api/v1/media/{id}", id).with(asUser(ana)).header(HttpHeaders.IF_NONE_MATCH, "\"" + id + "\""))
                .andExpect(status().isNotModified());
    }

    @Test
    void unsupportedFilesAreRejectedWithClearMessages() throws Exception {
        TestUser ana = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Tipos", "PRIVATE");

        upload(mvc, ana, eventId, TestImages.mp4Header(), "video.mp4", "video/mp4")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VIDEO"));
        upload(mvc, ana, eventId, "ID3\u0004\0\0\0\0\0\0".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1),
                "audio.mp3", "audio/mpeg")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AUDIO"));
        upload(mvc, ana, eventId, TestImages.heicHeader(), "IMG_0001.HEIC", "image/heic")
                .andExpect(jsonPath("$.code").value("IMAGE_FORMAT_NOT_SUPPORTED"));
        upload(mvc, ana, eventId, "<svg/>".getBytes(), "x.svg", "image/svg+xml")
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        upload(mvc, ana, eventId, new byte[0], "vazio.png", "image/png")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FILE"));

        Integer rows = jdbc.queryForObject("select count(*) from media_file where event_id = ?", Integer.class, eventId);
        assertThat(rows).isZero();
    }

    @Test
    void audioIsLimitedToThirtySecondsAndSupportsRangeRequests() throws Exception {
        TestUser ana = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Áudios", "PUBLIC");
        byte[] wav = TestAudio.wav(2);
        String body = upload(mvc, ana, eventId, wav, "lembranca.wav", "audio/wav")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("AUDIO"))
                .andExpect(jsonPath("$.durationMs").value(2000))
                .andReturn().getResponse().getContentAsString();
        UUID audio = UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.id"));
        publish(mvc, ana, eventId,
                "{\"mediaIds\":[\"%s\"],\"visibility\":\"PUBLIC\"}".formatted(audio))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/media/{id}", audio).header(HttpHeaders.RANGE, "bytes=0-9"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 0-9/" + wav.length))
                .andExpect(content().bytes(java.util.Arrays.copyOfRange(wav, 0, 10)));

        upload(mvc, ana, eventId, TestAudio.wav(31), "longo.wav", "audio/wav")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AUDIO_TOO_LONG"));
    }

    @Test
    void videoIsLimitedToSixtySecondsAndCanOnlyBelongToAMemory() throws Exception {
        TestUser ana = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Vídeos", "PUBLIC");
        byte[] mp4 = TestVideo.mp4(60, 1280, 720);
        String body = upload(mvc, ana, eventId, mp4, "festa.mp4", "application/octet-stream")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("VIDEO"))
                .andExpect(jsonPath("$.contentType").value("video/mp4"))
                .andExpect(jsonPath("$.durationMs").value(60_000))
                .andExpect(jsonPath("$.width").value(1280))
                .andExpect(jsonPath("$.height").value(720))
                .andReturn().getResponse().getContentAsString();
        UUID video = UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.id"));

        publish(mvc, ana, eventId,
                "{\"mediaIds\":[\"%s\"],\"visibility\":\"PUBLIC\"}".formatted(video))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.media[0].kind").value("VIDEO"));
        mvc.perform(get("/api/v1/media/{id}", video).header(HttpHeaders.RANGE, "bytes=0-9"))
                .andExpect(status().isPartialContent())
                .andExpect(content().contentType("video/mp4"));

        upload(mvc, ana, eventId, TestVideo.mp4(61, 1280, 720), "longo.mp4", "video/mp4")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VIDEO_TOO_LONG"));

        String firstBody = upload(mvc, ana, eventId, TestVideo.mp4(5, 640, 480), "um.mp4", "video/mp4")
                .andReturn().getResponse().getContentAsString();
        String secondBody = upload(mvc, ana, eventId, TestVideo.mp4(5, 640, 480), "dois.mp4", "video/mp4")
                .andReturn().getResponse().getContentAsString();
        UUID first = UUID.fromString(com.jayway.jsonpath.JsonPath.read(firstBody, "$.id"));
        UUID second = UUID.fromString(com.jayway.jsonpath.JsonPath.read(secondBody, "$.id"));
        publish(mvc, ana, eventId, "{\"mediaIds\":[\"%s\",\"%s\"]}".formatted(first, second))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TOO_MANY_VIDEO"));
    }

    @Test
    void uploadingRequiresParticipationAndAnOpenMural() throws Exception {
        TestUser ana = users.active();
        UUID publicEvent = EventApi.create(mvc, ana, "Público", "PUBLIC");
        UUID privateEvent = EventApi.create(mvc, ana, "Privado", "PRIVATE");
        byte[] png = TestImages.png(10, 10);

        upload(mvc, users.active(), publicEvent, png, "a.png", "image/png")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVENT_PARTICIPANTS_ONLY"));
        upload(mvc, users.active(), privateEvent, png, "a.png", "image/png")
                .andExpect(status().isNotFound());

        mvc.perform(patch("/api/v1/events/{id}/mural", privateEvent).with(asUser(ana)).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"));
        upload(mvc, ana, privateEvent, png, "a.png", "image/png")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MURAL_CLOSED"));
    }

    @Test
    void pendingImageIsOnlyVisibleToItsUploader() throws Exception {
        TestUser ana = users.active();
        TestUser bia = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Pendente", "PUBLIC");
        addParticipant(jdbc, eventId, bia, "PARTICIPANT");
        UUID image = uploadImage(mvc, ana, eventId);

        mvc.perform(get("/api/v1/media/{id}", image).with(asUser(ana))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/media/{id}", image).with(asUser(bia)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
        mvc.perform(get("/api/v1/media/{id}", image)).andExpect(status().isNotFound());
    }

    @Test
    void publishedImageFollowsTheMemoryVisibility() throws Exception {
        TestUser ana = users.active();
        TestUser guest = users.active();
        UUID publicEvent = EventApi.create(mvc, ana, "Galeria", "PUBLIC");
        UUID privateEvent = EventApi.create(mvc, ana, "Galeria privada", "PRIVATE");
        addParticipant(jdbc, publicEvent, guest, "PARTICIPANT");

        UUID publicImage = uploadImage(mvc, ana, publicEvent);
        publish(mvc, ana, publicEvent, "{\"mediaIds\":[\"%s\"],\"visibility\":\"PUBLIC\"}".formatted(publicImage));
        UUID restrictedImage = uploadImage(mvc, ana, publicEvent);
        publish(mvc, ana, publicEvent, "{\"mediaIds\":[\"%s\"]}".formatted(restrictedImage));
        UUID privateImage = uploadImage(mvc, ana, privateEvent);
        publish(mvc, ana, privateEvent, "{\"mediaIds\":[\"%s\"]}".formatted(privateImage));

        // Anônimo: só a imagem da memória pública de evento público.
        mvc.perform(get("/api/v1/media/{id}", publicImage)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/media/{id}", restrictedImage)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/media/{id}", privateImage)).andExpect(status().isNotFound());

        // Participante do evento público vê a restrita, mas não a do evento privado.
        mvc.perform(get("/api/v1/media/{id}", restrictedImage).with(asUser(guest))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/media/{id}", privateImage).with(asUser(guest))).andExpect(status().isNotFound());
    }

    @Test
    void uploaderCanDiscardAPendingImage() throws Exception {
        TestUser ana = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Descartar", "PRIVATE");
        UUID image = uploadImage(mvc, ana, eventId);
        String key = storageKey(image);

        mvc.perform(delete("/api/v1/media/{id}", image).with(asUser(users.active())).with(xsrf()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/media/{id}", image).with(asUser(ana)).with(xsrf()))
                .andExpect(status().isNoContent());
        assertThat(storage.contains(key)).isFalse();

        UUID published = uploadImage(mvc, ana, eventId);
        publish(mvc, ana, eventId, "{\"mediaIds\":[\"%s\"]}".formatted(published));
        mvc.perform(delete("/api/v1/media/{id}", published).with(asUser(ana)).with(xsrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MEDIA_ALREADY_PUBLISHED"));
    }

    @Test
    void forgottenPendingImagesArePurged() throws Exception {
        TestUser ana = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Órfãs", "PRIVATE");
        UUID old = uploadImage(mvc, ana, eventId);
        UUID recent = uploadImage(mvc, ana, eventId);
        String oldKey = storageKey(old);
        jdbc.update("update media_file set created_at = now() - interval '2 days' where id = ?", old);

        assertThat(mediaService.purgeOrphans()).isGreaterThanOrEqualTo(1);

        assertThat(storage.contains(oldKey)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from media_file where id = ?", Integer.class, old)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from media_file where id = ?", Integer.class, recent))
                .isOne();
    }

    @Test
    void missingFilePartIsAValidationError() throws Exception {
        TestUser ana = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Sem arquivo", "PRIVATE");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/v1/events/{id}/media", eventId).with(asUser(ana)).with(xsrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
