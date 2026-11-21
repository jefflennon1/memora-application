package br.com.memora.interaction;

import static br.com.memora.support.EventApi.addParticipant;
import static br.com.memora.support.InteractionApi.changeMural;
import static br.com.memora.support.InteractionApi.comment;
import static br.com.memora.support.InteractionApi.commentText;
import static br.com.memora.support.InteractionApi.deleteComment;
import static br.com.memora.support.InteractionApi.getComment;
import static br.com.memora.support.InteractionApi.getMemory;
import static br.com.memora.support.InteractionApi.listComments;
import static br.com.memora.support.InteractionApi.listReplies;
import static br.com.memora.support.InteractionApi.patchComment;
import static br.com.memora.support.InteractionApi.patchMemory;
import static br.com.memora.support.InteractionApi.reply;
import static br.com.memora.support.InteractionApi.replyText;
import static br.com.memora.support.MemoryApi.publishText;
import static br.com.memora.support.MemoryApi.uploadAudio;
import static br.com.memora.support.MemoryApi.upload;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static br.com.memora.support.WebSecuritySupport.asUser;
import static br.com.memora.support.WebSecuritySupport.xsrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.memora.support.EventApi;
import br.com.memora.support.IntegrationTest;
import br.com.memora.support.TestUsers;
import br.com.memora.support.TestVideo;
import br.com.memora.support.TestUsers.TestUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Entrega 6 — comentários, respostas e @menções, e quem vê o quê.
 */
@IntegrationTest
class CommentsIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestUsers users;

    @Autowired
    JdbcTemplate jdbc;

    /** Evento privado da Ana com a Bia participando, e uma memória da Ana. */
    private record Scene(TestUser ana, TestUser bia, UUID eventId, UUID memoryId) {
    }

    private Scene privateScene() throws Exception {
        TestUser ana = users.active();
        TestUser bia = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Chá do Liam", "PRIVATE");
        addParticipant(jdbc, eventId, bia, "PARTICIPANT");
        UUID memoryId = publishText(mvc, ana, eventId, "Que tarde linda", null);
        return new Scene(ana, bia, eventId, memoryId);
    }

    // ------------------------------------------------------------------ comentar e responder

    @Test
    void participantsCommentAndReplyInTwoLevels() throws Exception {
        Scene s = privateScene();

        UUID commentId = commentText(mvc, s.bia(), s.memoryId(), "  Que lindo! 😍 ", null);
        getComment(mvc, s.bia(), commentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memoryId").value(s.memoryId().toString()))
                .andExpect(jsonPath("$.parentId").doesNotExist())
                .andExpect(jsonPath("$.author.id").value(s.bia().id().toString()))
                .andExpect(jsonPath("$.text").value("Que lindo! 😍"))
                .andExpect(jsonPath("$.visibility").value("PARTICIPANTS_ONLY"))
                .andExpect(jsonPath("$.replyCount").value(0))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.editedAt").doesNotExist());

        reply(mvc, s.ana(), commentId, "{\"text\":\"Obrigada!\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(commentId.toString()))
                .andExpect(jsonPath("$.memoryId").value(s.memoryId().toString()))
                .andExpect(jsonPath("$.replyCount").doesNotExist());
        UUID second = replyText(mvc, s.bia(), commentId, "De nada", null);

        listComments(mvc, s.ana(), s.memoryId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(commentId.toString()))
                .andExpect(jsonPath("$.items[0].replyCount").value(2))
                .andExpect(jsonPath("$.items[0].mine").value(false));
        listReplies(mvc, s.ana(), commentId)
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].text").value("Obrigada!"))
                .andExpect(jsonPath("$.items[1].id").value(second.toString()));

        // A memória mostra quantos comentários e respostas há.
        getMemory(mvc, s.ana(), s.memoryId()).andExpect(jsonPath("$.commentCount").value(3));

        // Não existe resposta de resposta.
        reply(mvc, s.ana(), second, "{\"text\":\"Mais uma\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REPLY_TO_REPLY_NOT_ALLOWED"));
    }

    @Test
    void textLimitsAreEnforced() throws Exception {
        Scene s = privateScene();

        comment(mvc, s.bia(), s.memoryId(), "{\"text\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMENT_EMPTY"));
        comment(mvc, s.bia(), s.memoryId(), "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMENT_EMPTY"));
        // 200 caracteres é o máximo; emojis contam como um caractere.
        comment(mvc, s.bia(), s.memoryId(), "{\"text\":\"" + "😀".repeat(200) + "\"}")
                .andExpect(status().isCreated());
        comment(mvc, s.bia(), s.memoryId(), "{\"text\":\"" + "a".repeat(201) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMENT_TEXT_TOO_LONG"));
    }

    @Test
    void commentsAndRepliesCanContainThirtySecondAudioWithoutText() throws Exception {
        Scene s = privateScene();
        UUID audio = uploadAudio(mvc, s.bia(), s.eventId(), 30);

        String body = comment(mvc, s.bia(), s.memoryId(), "{\"audioId\":\"%s\"}".formatted(audio))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").doesNotExist())
                .andExpect(jsonPath("$.audio.id").value(audio.toString()))
                .andExpect(jsonPath("$.audio.durationMs").value(30000))
                .andReturn().getResponse().getContentAsString();
        UUID commentId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.id"));

        mvc.perform(get("/api/v1/comments/{id}/audio", commentId).with(asUser(s.ana())))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType("audio/wav"));
        mvc.perform(get("/api/v1/comments/{id}/audio", commentId).with(asUser(users.active())))
                .andExpect(status().isNotFound());

        patchComment(mvc, s.bia(), commentId, "{\"text\":\"Agora em texto\",\"removeAudio\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Agora em texto"))
                .andExpect(jsonPath("$.audio").doesNotExist());
    }

    @Test
    void videosCannotBeAttachedToComments() throws Exception {
        Scene s = privateScene();
        String uploaded = upload(mvc, s.bia(), s.eventId(), TestVideo.mp4(10, 640, 480), "festa.mp4", "video/mp4")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID video = UUID.fromString(com.jayway.jsonpath.JsonPath.read(uploaded, "$.id"));

        comment(mvc, s.bia(), s.memoryId(), "{\"audioId\":\"%s\"}".formatted(video))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_MEDIA"));
    }

    @Test
    void onlyParticipantsCommentAndOnlyWhileTheMuralIsOpen() throws Exception {
        TestUser ana = users.active();
        TestUser stranger = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Aberto", "PUBLIC");
        UUID memoryId = publishText(mvc, ana, eventId, "Memória pública", "PUBLIC");
        UUID commentId = commentText(mvc, ana, memoryId, "Primeiro", "PUBLIC");

        comment(mvc, stranger, memoryId, "{\"text\":\"Oi\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EVENT_PARTICIPANTS_ONLY"));
        reply(mvc, stranger, commentId, "{\"text\":\"Oi\"}")
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/memories/{id}/comments", memoryId).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Oi\"}"))
                .andExpect(status().isUnauthorized());

        changeMural(mvc, ana, eventId, "CLOSED").andExpect(status().isOk());
        comment(mvc, ana, memoryId, "{\"text\":\"Depois\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MURAL_CLOSED"));
        reply(mvc, ana, commentId, "{\"text\":\"Depois\"}")
                .andExpect(status().isUnprocessableEntity());
        patchComment(mvc, ana, commentId, "{\"text\":\"Editado\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MURAL_CLOSED"));
        // Excluir o próprio comentário continua possível.
        deleteComment(mvc, ana, commentId).andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ @menções

    @Test
    void mentionsPointToThePersonAndFollowUsernameChanges() throws Exception {
        Scene s = privateScene();
        TestUser outsider = users.active();

        String text = "Olha você aqui, @%s! E @%s também.".formatted(s.bia().username().toUpperCase(),
                outsider.username());
        UUID commentId = commentText(mvc, s.ana(), s.memoryId(), text, null);

        // Só quem participa do evento é mencionado; o resto continua texto comum.
        String biaAt = "@" + s.bia().username();
        int start = "Olha você aqui, ".length();
        getComment(mvc, s.ana(), commentId)
                .andExpect(jsonPath("$.text").value("Olha você aqui, %s! E @%s também.".formatted(biaAt,
                        outsider.username())))
                .andExpect(jsonPath("$.mentions.length()").value(1))
                .andExpect(jsonPath("$.mentions[0].start").value(start))
                .andExpect(jsonPath("$.mentions[0].length").value(biaAt.length()))
                .andExpect(jsonPath("$.mentions[0].person.id").value(s.bia().id().toString()))
                .andExpect(jsonPath("$.mentions[0].person.username").value(s.bia().username()));

        // A Bia troca o @usuário: a menção mostra o novo.
        String renamed = TestUsers.randomUsername() + "longo";
        mvc.perform(patch("/api/v1/users/me").with(asUser(s.bia())).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + renamed + "\"}"))
                .andExpect(status().isOk());
        getComment(mvc, s.ana(), commentId)
                .andExpect(jsonPath("$.text").value("Olha você aqui, @%s! E @%s também.".formatted(renamed,
                        outsider.username())))
                .andExpect(jsonPath("$.mentions[0].start").value(start))
                .andExpect(jsonPath("$.mentions[0].length").value(renamed.length() + 1));

        // Conta excluída: "Usuário excluído".
        jdbc.update("update app_user set status = 'DELETED' where id = ?", s.bia().id());
        getComment(mvc, s.ana(), commentId)
                .andExpect(jsonPath("$.text").value("Olha você aqui, Usuário excluído! E @%s também.".formatted(
                        outsider.username())))
                .andExpect(jsonPath("$.mentions[0].length").value("Usuário excluído".length()))
                .andExpect(jsonPath("$.mentions[0].person.profileHidden").value(true))
                .andExpect(jsonPath("$.mentions[0].person.username").doesNotExist());
    }

    @Test
    void aMentionedPrivateProfileStaysHiddenFromVisitors() throws Exception {
        TestUser ana = users.active();
        TestUser bia = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Festa aberta", "PUBLIC");
        addParticipant(jdbc, eventId, bia, "PARTICIPANT");
        mvc.perform(patch("/api/v1/users/me").with(asUser(bia)).with(xsrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"profilePrivacy\":\"PRIVATE\"}"))
                .andExpect(status().isOk());
        UUID memoryId = publishText(mvc, ana, eventId, "Para todos", "PUBLIC");
        UUID commentId = commentText(mvc, ana, memoryId, "Valeu, @" + bia.username(), "PUBLIC");

        getComment(mvc, null, commentId)
                .andExpect(jsonPath("$.text").value("Valeu, Perfil privado"))
                .andExpect(jsonPath("$.mentions[0].person.profileHidden").value(true))
                .andExpect(jsonPath("$.mentions[0].person.username").doesNotExist());
        getComment(mvc, ana, commentId).andExpect(jsonPath("$.text").value("Valeu, @" + bia.username()));
    }

    @Test
    void editingRecomputesMentionsAndKeepsThePublicationDate() throws Exception {
        Scene s = privateScene();
        UUID commentId = commentText(mvc, s.ana(), s.memoryId(), "Sem menção", null);
        String createdAt = com.jayway.jsonpath.JsonPath.read(getComment(mvc, s.ana(), commentId).andReturn()
                .getResponse().getContentAsString(), "$.createdAt");

        patchComment(mvc, s.ana(), commentId, "{\"text\":\"Agora com @%s\"}".formatted(s.bia().username()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mentions[0].person.id").value(s.bia().id().toString()))
                .andExpect(jsonPath("$.createdAt").value(createdAt))
                .andExpect(jsonPath("$.editedAt").exists());

        // Só o autor edita e exclui.
        patchComment(mvc, s.bia(), commentId, "{\"text\":\"Invasão\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMENT_AUTHOR_ONLY"));
        deleteComment(mvc, s.bia(), commentId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMENT_AUTHOR_ONLY"));
    }

    // ------------------------------------------------------------------ exclusão

    @Test
    void deletingACommentDeletesItsRepliesAndDeletingTheMemoryDeletesEverything() throws Exception {
        Scene s = privateScene();
        UUID first = commentText(mvc, s.bia(), s.memoryId(), "Comentário", null);
        UUID firstReply = replyText(mvc, s.ana(), first, "Resposta", null);
        UUID second = commentText(mvc, s.bia(), s.memoryId(), "Outro", null);

        deleteComment(mvc, s.bia(), first).andExpect(status().isNoContent());
        getComment(mvc, s.ana(), first)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
        getComment(mvc, s.ana(), firstReply).andExpect(status().isNotFound());
        getMemory(mvc, s.ana(), s.memoryId()).andExpect(jsonPath("$.commentCount").value(1));

        mvc.perform(delete("/api/v1/memories/{id}", s.memoryId()).with(asUser(s.ana())).with(xsrf()))
                .andExpect(status().isNoContent());
        getComment(mvc, s.bia(), second).andExpect(status().isNotFound());
        Integer left = jdbc.queryForObject("select count(*) from comment where memory_id = ?", Integer.class,
                s.memoryId());
        org.assertj.core.api.Assertions.assertThat(left).isZero();
    }

    // ------------------------------------------------------------------ privacidade

    @Test
    void outsidersNeverSeeCommentsOfAPrivateEvent() throws Exception {
        Scene s = privateScene();
        UUID commentId = commentText(mvc, s.bia(), s.memoryId(), "Segredo", null);
        TestUser stranger = users.active();

        for (TestUser outsider : new TestUser[] {stranger, null}) {
            listComments(mvc, outsider, s.memoryId())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MEMORY_NOT_FOUND"));
            getComment(mvc, outsider, commentId)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
            listReplies(mvc, outsider, commentId).andExpect(status().isNotFound());
        }
        // Inclusive tentando responder, editar ou excluir.
        reply(mvc, stranger, commentId, "{\"text\":\"Oi\"}").andExpect(status().isNotFound());
        patchComment(mvc, stranger, commentId, "{\"text\":\"Oi\"}").andExpect(status().isNotFound());
        deleteComment(mvc, stranger, commentId).andExpect(status().isNotFound());
    }

    @Test
    void visitorsOfAPublicEventSeeOnlyPublicCommentsAndCountOnlyThose() throws Exception {
        TestUser ana = users.active();
        TestUser bia = users.active();
        TestUser stranger = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Festa aberta", "PUBLIC");
        addParticipant(jdbc, eventId, bia, "PARTICIPANT");
        UUID memoryId = publishText(mvc, ana, eventId, "Memória pública", "PUBLIC");

        UUID open = commentText(mvc, bia, memoryId, "Público", "PUBLIC");
        UUID restricted = commentText(mvc, ana, memoryId, "Só para nós", null);
        replyText(mvc, ana, open, "Resposta pública", "PUBLIC");
        UUID restrictedReply = replyText(mvc, ana, open, "Resposta restrita", null);

        for (TestUser visitor : new TestUser[] {stranger, null}) {
            listComments(mvc, visitor, memoryId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalItems").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(open.toString()))
                    .andExpect(jsonPath("$.items[0].replyCount").value(1));
            listReplies(mvc, visitor, open)
                    .andExpect(jsonPath("$.totalItems").value(1))
                    .andExpect(jsonPath("$.items[0].text").value("Resposta pública"));
            getComment(mvc, visitor, restricted).andExpect(status().isNotFound());
            getComment(mvc, visitor, restrictedReply).andExpect(status().isNotFound());
            // Comentários restritos não aparecem nem na contagem.
            getMemory(mvc, visitor, memoryId).andExpect(jsonPath("$.commentCount").value(2));
        }
        getMemory(mvc, bia, memoryId).andExpect(jsonPath("$.commentCount").value(4));
        listComments(mvc, bia, memoryId).andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void aCommentIsNeverMorePublicThanItsMemoryNorAReplyThanItsComment() throws Exception {
        TestUser ana = users.active();
        UUID publicEvent = EventApi.create(mvc, ana, "Aberto", "PUBLIC");
        UUID restrictedMemory = publishText(mvc, ana, publicEvent, "Só participantes", null);
        UUID publicMemory = publishText(mvc, ana, publicEvent, "Para todos", "PUBLIC");

        comment(mvc, ana, restrictedMemory, "{\"text\":\"Oi\",\"visibility\":\"PUBLIC\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COMMENT_PUBLIC_REQUIRES_PUBLIC_MEMORY"));

        UUID restricted = commentText(mvc, ana, publicMemory, "Restrito", null);
        reply(mvc, ana, restricted, "{\"text\":\"Oi\",\"visibility\":\"PUBLIC\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REPLY_PUBLIC_REQUIRES_PUBLIC_COMMENT"));
        patchComment(mvc, ana, restricted, "{\"visibility\":\"PUBLIC\"}").andExpect(status().isOk());

        UUID privateEvent = EventApi.create(mvc, ana, "Fechado", "PRIVATE");
        UUID privateMemory = publishText(mvc, ana, privateEvent, "Nossa", null);
        comment(mvc, ana, privateMemory, "{\"text\":\"Oi\",\"visibility\":\"PUBLIC\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COMMENT_PUBLIC_REQUIRES_PUBLIC_MEMORY"));
    }

    @Test
    void restrictingAMemoryOrACommentRestrictsWhatHangsBelowIt() throws Exception {
        TestUser ana = users.active();
        UUID eventId = EventApi.create(mvc, ana, "Aberto", "PUBLIC");
        UUID memoryId = publishText(mvc, ana, eventId, "Para todos", "PUBLIC");
        UUID first = commentText(mvc, ana, memoryId, "Público 1", "PUBLIC");
        UUID firstReply = replyText(mvc, ana, first, "Resposta pública", "PUBLIC");
        UUID second = commentText(mvc, ana, memoryId, "Público 2", "PUBLIC");
        replyText(mvc, ana, second, "Outra resposta pública", "PUBLIC");

        // O comentário deixa de ser público: a resposta dele também.
        patchComment(mvc, ana, first, "{\"visibility\":\"PARTICIPANTS_ONLY\"}").andExpect(status().isOk());
        getComment(mvc, ana, firstReply).andExpect(jsonPath("$.visibility").value("PARTICIPANTS_ONLY"));
        patchComment(mvc, ana, first, "{\"visibility\":\"PUBLIC\"}").andExpect(status().isOk());
        getComment(mvc, null, firstReply).andExpect(status().isNotFound());

        // A memória deixa de ser pública e volta: nada do que foi comentado volta a ficar exposto.
        patchMemory(mvc, ana, memoryId, "{\"visibility\":\"PARTICIPANTS_ONLY\"}").andExpect(status().isOk());
        patchMemory(mvc, ana, memoryId, "{\"visibility\":\"PUBLIC\"}").andExpect(status().isOk());
        listComments(mvc, null, memoryId).andExpect(jsonPath("$.totalItems").value(0));
        getMemory(mvc, null, memoryId).andExpect(jsonPath("$.commentCount").value(0));
        getComment(mvc, ana, second).andExpect(jsonPath("$.visibility").value("PARTICIPANTS_ONLY"));
    }
}
