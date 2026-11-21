package br.com.memora.memory;

import static br.com.memora.support.EventApi.addParticipant;
import static br.com.memora.support.MemoryApi.publish;
import static br.com.memora.support.MemoryApi.publishText;
import static br.com.memora.support.MemoryApi.uploadImage;
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
 * Entrega 5 — publicar, ver, editar e excluir memórias, e quem vê o quê.
 */
@IntegrationTest
class MemoriesIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions patchMemory(TestUser user, UUID memoryId, String json) throws Exception {
        return mvc.perform(patch("/api/v1/memories/{id}", memoryId).with(asUser(user)).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions changeMural(TestUser user, UUID eventId, String status) throws Exception {
        return mvc.perform(patch("/api/v1/events/{id}/mural", eventId).with(asUser(user)).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
    }

    // ------------------------------------------------------------------ publicar

    @Test
    void participantPublishesTextAndImagesInOrder() throws Exception {
        TestUser ana = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Chá do Liam", "PRIVATE");
        UUID first = uploadImage(mvc, ana, eventId);
        UUID second = uploadImage(mvc, ana, eventId);

        publish(mvc, ana, eventId, """
                {"text":"  Que tarde linda! 💛 ","mediaIds":["%s","%s"]}""".formatted(second, first))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("Que tarde linda! 💛"))
                .andExpect(jsonPath("$.visibility").value("PARTICIPANTS_ONLY"))
                .andExpect(jsonPath("$.author.id").value(ana.id().toString()))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.media.length()").value(2))
                .andExpect(jsonPath("$.media[0].id").value(second.toString()))
                .andExpect(jsonPath("$.media[1].id").value(first.toString()))
                .andExpect(jsonPath("$.media[0].url").value("/api/v1/media/" + second))
                .andExpect(jsonPath("$.media[0].width").value(40))
                .andExpect(jsonPath("$.media[0].height").value(30))
                .andExpect(jsonPath("$.editedAt").doesNotExist());
    }

    @Test
    void contentLimitsAreEnforced() throws Exception {
        TestUser ana = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Limites", "PRIVATE");

        publish(mvc, ana, eventId, "{\"text\":\"   \"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MEMORY_EMPTY"));

        // 500 caracteres é o máximo; emojis contam como um caractere.
        publish(mvc, ana, eventId, "{\"text\":\"" + "😀".repeat(500) + "\"}").andExpect(status().isCreated());
        publish(mvc, ana, eventId, "{\"text\":\"" + "a".repeat(501) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMORY_TEXT_TOO_LONG"));

        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            ids.append(i == 0 ? "" : ",").append('"').append(uploadImage(mvc, ana, eventId)).append('"');
        }
        publish(mvc, ana, eventId, "{\"mediaIds\":[" + ids + "]}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TOO_MANY_MEDIA"));

        UUID one = uploadImage(mvc, ana, eventId);
        publish(mvc, ana, eventId, "{\"mediaIds\":[\"%s\",\"%s\"]}".formatted(one, one))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_MEDIA"));
    }

    @Test
    void imagesFromSomeoneElseOrAlreadyPublishedCannotBeReused() throws Exception {
        TestUser ana = users.active();
        TestUser bia = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Reuso", "PRIVATE");
        UUID otherEvent = EventApi.create(mvc, ana, "Outro", "PRIVATE");
        addParticipant(jdbc, eventId, bia, "PARTICIPANT");

        UUID anasImage = uploadImage(mvc, ana, eventId);
        publish(mvc, bia, eventId, "{\"mediaIds\":[\"%s\"]}".formatted(anasImage))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MEDIA"));

        UUID fromOtherEvent = uploadImage(mvc, ana, otherEvent);
        publish(mvc, ana, eventId, "{\"mediaIds\":[\"%s\"]}".formatted(fromOtherEvent))
                .andExpect(status().isBadRequest());

        publish(mvc, ana, eventId, "{\"mediaIds\":[\"%s\"]}".formatted(anasImage)).andExpect(status().isCreated());
        publish(mvc, ana, eventId, "{\"mediaIds\":[\"%s\"]}".formatted(anasImage))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MEDIA"));

        publish(mvc, ana, eventId, "{\"mediaIds\":[\"%s\"]}".formatted(UUID.randomUUID()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyParticipantsPublishAndOnlyWhileTheMuralIsOpen() throws Exception {
        TestUser ana = users.active();
        UUID publicEvent = EventApi.create(mvc, ana, "Aberto", "PUBLIC");
        UUID privateEvent = EventApi.create(mvc, ana, "Fechado", "PRIVATE");
        TestUser stranger = users.active();

        publish(mvc, stranger, publicEvent, "{\"text\":\"oi\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVENT_PARTICIPANTS_ONLY"));
        publish(mvc, stranger, privateEvent, "{\"text\":\"oi\"}")
                .andExpect(status().isNotFound());

        changeMural(ana, privateEvent, "CLOSED");
        publish(mvc, ana, privateEvent, "{\"text\":\"oi\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MURAL_CLOSED"));

        publish(mvc, users.pending(), publicEvent, "{\"text\":\"oi\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ONBOARDING_REQUIRED"));
    }

    // ------------------------------------------------------------------ quem vê o quê

    @Test
    void visibilityMatrix() throws Exception {
        TestUser ana = users.active();
        TestUser guest = users.active();
        TestUser stranger = users.active();
        UUID publicEvent = EventApi.create(mvc, ana, "Show", "PUBLIC");
        addParticipant(jdbc, publicEvent, guest, "PARTICIPANT");
        UUID open = publishText(mvc, ana, publicEvent, "para todos", "PUBLIC");
        UUID restricted = publishText(mvc, ana, publicEvent, "só nossa", null);

        // Participante: tudo.
        mvc.perform(get("/api/v1/events/{id}/memories", publicEvent).with(asUser(guest)))
                .andExpect(jsonPath("$.totalItems").value(2));

        // Quem não participa (e visitante anônimo): só a pública, e a restrita "não existe".
        mvc.perform(get("/api/v1/events/{id}/memories", publicEvent).with(asUser(stranger)))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(open.toString()))
                .andExpect(jsonPath("$.items[0].mine").value(false));
        mvc.perform(get("/api/v1/events/{id}/memories", publicEvent))
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/v1/memories/{id}", restricted).with(asUser(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMORY_NOT_FOUND"));
        mvc.perform(get("/api/v1/memories/{id}", open)).andExpect(status().isOk());
    }

    @Test
    void privateEventMemoriesNeverLeak() throws Exception {
        TestUser ana = users.active();
        UUID privateEvent = EventApi.create(mvc, ana, "Segredo", "PRIVATE");
        UUID memory = publishText(mvc, ana, privateEvent, "segredo", null);

        publish(mvc, ana, privateEvent, "{\"text\":\"x\",\"visibility\":\"PUBLIC\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MEMORY_PUBLIC_REQUIRES_PUBLIC_EVENT"));

        mvc.perform(get("/api/v1/events/{id}/memories", privateEvent).with(asUser(users.active())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/events/{id}/memories", privateEvent)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/memories/{id}", memory).with(asUser(users.active())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMORY_NOT_FOUND"));
    }

    @Test
    void memoriesCreatedWhileTheEventWasPrivateStayRestrictedUntilTheAuthorDecides() throws Exception {
        TestUser ana = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Vira público", "PRIVATE");
        UUID memory = publishText(mvc, ana, eventId, "de quando era privado", null);

        mvc.perform(patch("/api/v1/events/{id}", eventId).with(asUser(ana)).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"privacy\":\"PUBLIC\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/events/{id}/memories", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));

        patchMemory(ana, memory, "{\"visibility\":\"PUBLIC\"}").andExpect(jsonPath("$.visibility").value("PUBLIC"));
        mvc.perform(get("/api/v1/events/{id}/memories", eventId)).andExpect(jsonPath("$.totalItems").value(1));

        // Voltar a privado esconde tudo de novo: a privacidade do evento prevalece.
        jdbc.update("update event set privacy = 'PRIVATE' where id = ?", eventId);
        mvc.perform(get("/api/v1/memories/{id}", memory)).andExpect(status().isNotFound());
    }

    @Test
    void muralIsChronologicalByDefaultAndPaginated() throws Exception {
        TestUser ana = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Ordem", "PRIVATE");
        UUID first = publishText(mvc, ana, eventId, "primeira", null);
        publishText(mvc, ana, eventId, "segunda", null);
        UUID third = publishText(mvc, ana, eventId, "terceira", null);

        mvc.perform(get("/api/v1/events/{id}/memories", eventId).param("size", "2").with(asUser(ana)))
                .andExpect(jsonPath("$.items[0].id").value(first.toString()))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get("/api/v1/events/{id}/memories", eventId).param("order", "NEWEST").with(asUser(ana)))
                .andExpect(jsonPath("$.items[0].id").value(third.toString()));
    }

    // ------------------------------------------------------------------ editar e excluir

    @Test
    void authorEditsWithoutChangingThePublicationDate() throws Exception {
        TestUser ana = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Edição", "PRIVATE");
        UUID keep = uploadImage(mvc, ana, eventId);
        UUID drop = uploadImage(mvc, ana, eventId);
        String created = publish(mvc, ana, eventId, "{\"text\":\"antes\",\"mediaIds\":[\"%s\",\"%s\"]}"
                .formatted(keep, drop)).andReturn().getResponse().getContentAsString();
        UUID memory = UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.id"));
        String createdAt = com.jayway.jsonpath.JsonPath.read(created, "$.createdAt");
        UUID added = uploadImage(mvc, ana, eventId);

        patchMemory(ana, memory, "{\"text\":\"depois\",\"mediaIds\":[\"%s\",\"%s\"]}".formatted(added, keep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("depois"))
                .andExpect(jsonPath("$.createdAt").value(createdAt))
                .andExpect(jsonPath("$.editedAt").exists())
                .andExpect(jsonPath("$.media[0].id").value(added.toString()))
                .andExpect(jsonPath("$.media[1].id").value(keep.toString()));

        // A imagem retirada deixa de existir.
        mvc.perform(get("/api/v1/media/{id}", drop).with(asUser(ana))).andExpect(status().isNotFound());

        // Apagar o texto mantendo as imagens; sem nada, a memória ficaria vazia.
        patchMemory(ana, memory, "{\"text\":null}").andExpect(jsonPath("$.text").doesNotExist());
        patchMemory(ana, memory, "{\"mediaIds\":[]}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MEMORY_EMPTY"));
    }

    @Test
    void onlyTheAuthorEditsOrDeletesAndEditingRequiresAnOpenMural() throws Exception {
        TestUser ana = users.active();
        TestUser co = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Autoria", "PRIVATE");
        addParticipant(jdbc, eventId, co, "CO_ORGANIZER");
        UUID memory = publishText(mvc, co, eventId, "da coorganizadora", null);

        patchMemory(ana, memory, "{\"text\":\"x\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMORY_AUTHOR_ONLY"));
        mvc.perform(delete("/api/v1/memories/{id}", memory).with(asUser(ana)).with(xsrf()))
                .andExpect(status().isForbidden());

        changeMural(ana, eventId, "CLOSED");
        patchMemory(co, memory, "{\"text\":\"x\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MURAL_CLOSED"));
        // Excluir o próprio conteúdo continua possível com o mural fechado.
        mvc.perform(delete("/api/v1/memories/{id}", memory).with(asUser(co)).with(xsrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/memories/{id}", memory).with(asUser(co))).andExpect(status().isNotFound());
    }

    @Test
    void deletingRemovesTheImagesToo() throws Exception {
        TestUser ana = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Excluir", "PRIVATE");
        UUID image = uploadImage(mvc, ana, eventId);
        String created = publish(mvc, ana, eventId, "{\"mediaIds\":[\"%s\"]}".formatted(image))
                .andReturn().getResponse().getContentAsString();
        UUID memory = UUID.fromString(com.jayway.jsonpath.JsonPath.read(created, "$.id"));

        mvc.perform(delete("/api/v1/memories/{id}", memory).with(asUser(ana)).with(xsrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/media/{id}", image).with(asUser(ana))).andExpect(status().isNotFound());
        Integer rows = jdbc.queryForObject("select count(*) from media_file where id = ?", Integer.class, image);
        org.assertj.core.api.Assertions.assertThat(rows).isZero();
    }

    @Test
    void removingAParticipantKeepsTheirMemories() throws Exception {
        TestUser ana = users.active();
        TestUser bia = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Saída", "PRIVATE");
        addParticipant(jdbc, eventId, bia, "PARTICIPANT");
        UUID memory = publishText(mvc, bia, eventId, "fico no mural", null);

        mvc.perform(delete("/api/v1/events/{id}/participants/{u}", eventId, bia.id()).with(asUser(ana)).with(xsrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/memories/{id}", memory).with(asUser(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author.id").value(bia.id().toString()));
        mvc.perform(get("/api/v1/memories/{id}", memory).with(asUser(bia))).andExpect(status().isNotFound());
    }
}
