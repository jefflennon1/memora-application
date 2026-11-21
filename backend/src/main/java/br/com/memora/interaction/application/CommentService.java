package br.com.memora.interaction.application;

import br.com.memora.event.application.EventAccess.EventContext;
import br.com.memora.interaction.domain.Comment;
import br.com.memora.interaction.domain.CommentRepository;
import br.com.memora.interaction.domain.CommentRepository.IdCount;
import br.com.memora.interaction.domain.CommentVisibility;
import br.com.memora.interaction.domain.Mention;
import br.com.memora.interaction.domain.MentionParser;
import br.com.memora.membership.application.PeopleDirectory;
import br.com.memora.membership.application.PeopleDirectory.PersonView;
import br.com.memora.memory.application.MemoryAccess;
import br.com.memora.memory.application.MediaService;
import br.com.memora.memory.application.MediaService.MediaView;
import br.com.memora.memory.domain.Memory;
import br.com.memora.memory.domain.MemoryVisibility;
import br.com.memora.shared.error.BusinessRuleException;
import br.com.memora.shared.error.ForbiddenException;
import br.com.memora.shared.error.NotFoundException;
import br.com.memora.shared.error.ValidationException;
import br.com.memora.shared.web.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comentários e respostas nas memórias.
 *
 * <ul>
 *   <li>Comentar e responder: participante, com o mural aberto. Até 200 caracteres. Só se responde a um
 *       comentário principal: não existe resposta de resposta.</li>
 *   <li>Visibilidade padrão {@code PARTICIPANTS_ONLY}. Um comentário só pode ser {@code PUBLIC} numa memória
 *       pública de evento público, e uma resposta só se o comentário também for público.</li>
 *   <li>Quem não participa vê só os comentários públicos de memórias que pode ver; os restritos não aparecem nem
 *       na contagem. Um comentário que a pessoa não pode ver responde {@code 404 COMMENT_NOT_FOUND}.</li>
 *   <li>Editar: só o autor, participante, com o mural aberto. Excluir: só o autor, também com o mural fechado;
 *       as respostas de um comentário principal são apagadas junto.</li>
 *   <li>@menções: só de quem participa do evento. Apontam para a pessoa, então mostram sempre o @usuário atual.</li>
 * </ul>
 */
@Service
public class CommentService {

    static final String DELETED_USER_LABEL = "Usuário excluído";
    static final String HIDDEN_USER_LABEL = "Perfil privado";

    private static final Sort CHRONOLOGICAL = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

    private final CommentRepository comments;
    private final MemoryAccess memories;
    private final PeopleDirectory people;
    private final MediaService media;
    private final Clock clock;

    public CommentService(CommentRepository comments, MemoryAccess memories, PeopleDirectory people,
                          MediaService media, Clock clock) {
        this.comments = comments;
        this.memories = memories;
        this.people = people;
        this.media = media;
        this.clock = clock;
    }

    @Transactional
    public CommentView comment(UUID memoryId, UUID authorId, WriteCommand command) {
        MemoryAccess.Visible visible = memories.requireVisible(memoryId, authorId);
        visible.event().requireParticipant();
        visible.event().requireMuralOpen();
        CommentVisibility visibility = resolveVisibility(command.visibility(), CommentVisibility.PARTICIPANTS_ONLY,
                null, visible);
        String text = Comment.normalizeText(command.text(), command.audioId() != null);

        Comment comment = Comment.write(memoryId, null, authorId, text, mentions(visible.event(), text), visibility,
                clock.instant());
        comments.saveAndFlush(comment);
        if (command.audioId() != null) {
            media.attachToComment(command.audioId(), comment.getId(), authorId, visible.event().eventId());
        }
        return toView(comment, 0L, authorId);
    }

    @Transactional
    public CommentView reply(UUID commentId, UUID authorId, WriteCommand command) {
        VisibleComment parent = requireVisible(commentId, authorId);
        EventContext event = parent.memory().event();
        event.requireParticipant();
        event.requireMuralOpen();
        if (parent.comment().isReply()) {
            throw new BusinessRuleException("REPLY_TO_REPLY_NOT_ALLOWED",
                    "Só é possível responder a um comentário principal.");
        }
        CommentVisibility visibility = resolveVisibility(command.visibility(), CommentVisibility.PARTICIPANTS_ONLY,
                parent.comment(), parent.memory());
        String text = Comment.normalizeText(command.text(), command.audioId() != null);

        Comment reply = Comment.write(parent.comment().getMemoryId(), commentId, authorId, text,
                mentions(event, text), visibility, clock.instant());
        comments.saveAndFlush(reply);
        if (command.audioId() != null) {
            media.attachToComment(command.audioId(), reply.getId(), authorId, event.eventId());
        }
        return toView(reply, null, authorId);
    }

    /** Comentários principais de uma memória, em ordem cronológica. */
    @Transactional(readOnly = true)
    public Page<CommentView> list(UUID memoryId, UUID viewerId, int page, int size) {
        MemoryAccess.Visible visible = memories.requireVisible(memoryId, viewerId);
        boolean participant = visible.event().isParticipant();
        Pageable pageable = chronological(page, size);
        Page<Comment> result = participant
                ? comments.findByMemoryIdAndParentIdIsNull(memoryId, pageable)
                : comments.findByMemoryIdAndParentIdIsNullAndVisibility(memoryId, CommentVisibility.PUBLIC,
                        pageable);
        List<UUID> ids = result.getContent().stream().map(Comment::getId).toList();
        Map<UUID, Long> replyCounts = ids.isEmpty() ? Map.of() : toMap(participant
                ? comments.countReplies(ids)
                : comments.countReplies(ids, CommentVisibility.PUBLIC));
        return toViews(result, viewerId, c -> replyCounts.getOrDefault(c.getId(), 0L));
    }

    /** Respostas de um comentário, em ordem cronológica. */
    @Transactional(readOnly = true)
    public Page<CommentView> replies(UUID commentId, UUID viewerId, int page, int size) {
        VisibleComment parent = requireVisible(commentId, viewerId);
        Pageable pageable = chronological(page, size);
        Page<Comment> result = parent.memory().event().isParticipant()
                ? comments.findByParentId(commentId, pageable)
                : comments.findByParentIdAndVisibility(commentId, CommentVisibility.PUBLIC, pageable);
        return toViews(result, viewerId, c -> null);
    }

    @Transactional(readOnly = true)
    public CommentView get(UUID commentId, UUID viewerId) {
        VisibleComment visible = requireVisible(commentId, viewerId);
        return toView(visible.comment(), replyCount(visible), viewerId);
    }

    /** Edição pelo autor: {@code null} mantém o texto ou a visibilidade. A data de publicação não muda. */
    @Transactional
    public CommentView update(UUID commentId, UUID userId, WriteCommand command) {
        VisibleComment visible = requireVisible(commentId, userId);
        Comment comment = visible.comment();
        requireAuthor(comment, userId);
        EventContext event = visible.memory().event();
        event.requireParticipant();
        event.requireMuralOpen();

        Comment parent = comment.isReply() ? comments.findById(comment.getParentId()).orElse(null) : null;
        CommentVisibility visibility = resolveVisibility(command.visibility(), comment.getVisibility(), parent,
                visible.memory());
        MediaView currentAudio = media.audiosForComments(List.of(commentId)).get(commentId);
        boolean resultingAudio = command.audioId() != null || (!command.removeAudio() && currentAudio != null);
        String text = command.text() == null
                ? comment.getText()
                : Comment.normalizeText(command.text(), resultingAudio);
        if (text == null && !resultingAudio) {
            throw new ValidationException("COMMENT_EMPTY", "Escreva alguma coisa ou adicione um áudio.");
        }
        List<Mention> mentions = text == null ? List.of() : mentions(event, text);
        boolean restricting = !comment.isReply() && comment.isPublic()
                && visibility == CommentVisibility.PARTICIPANTS_ONLY;

        comment.edit(text, command.text() != null, mentions, visibility, clock.instant());
        if (command.removeAudio() || command.audioId() != null) {
            media.removeCommentAudios(List.of(commentId));
        }
        if (command.audioId() != null) {
            media.attachToComment(command.audioId(), commentId, userId, event.eventId());
        }
        comments.flush();
        if (restricting) {
            comments.restrictReplies(commentId);
        }
        return toView(comment, replyCount(visible), userId);
    }

    /** Exclusão pelo autor, também com o mural fechado. As respostas saem junto (pelo banco). */
    @Transactional
    public void delete(UUID commentId, UUID userId) {
        Comment comment = requireVisible(commentId, userId).comment();
        requireAuthor(comment, userId);
        media.removeCommentAudios(comments.findTreeIds(commentId));
        comments.delete(comment);
        comments.flush();
    }

    /** Id do áudio depois de aplicar exatamente as mesmas regras de visibilidade do comentário. */
    @Transactional(readOnly = true)
    public UUID audioId(UUID commentId, UUID viewerId) {
        requireVisible(commentId, viewerId);
        MediaView audio = media.audiosForComments(List.of(commentId)).get(commentId);
        if (audio == null) throw notFound();
        return audio.id();
    }

    // ------------------------------------------------------------------ acesso e regras

    /**
     * O comentário, se quem consulta pode vê-lo: precisa ver a memória e, se não participa do evento, o comentário
     * (e, numa resposta, o comentário principal) precisa ser público.
     */
    private VisibleComment requireVisible(UUID commentId, UUID viewerId) {
        Optional<Comment> found = comments.findById(commentId);
        if (found.isEmpty()) {
            throw notFound();
        }
        Comment comment = found.get();
        MemoryAccess.Visible memory = memories.findVisible(comment.getMemoryId(), viewerId).orElseThrow(
                CommentService::notFound);
        if (!memory.event().isParticipant()) {
            boolean parentPublic = !comment.isReply()
                    || comments.findById(comment.getParentId()).map(Comment::isPublic).orElse(false);
            if (!comment.isPublic() || !parentPublic) {
                throw notFound();
            }
        }
        return new VisibleComment(comment, memory);
    }

    private static CommentVisibility resolveVisibility(CommentVisibility requested, CommentVisibility fallback,
                                                       Comment parent, MemoryAccess.Visible memory) {
        if (requested != CommentVisibility.PUBLIC) {
            return requested == null ? fallback : requested;
        }
        Memory m = memory.memory();
        boolean memoryPublic = m.getVisibility() == MemoryVisibility.PUBLIC && memory.event().isPublic();
        if (!memoryPublic) {
            throw new BusinessRuleException("COMMENT_PUBLIC_REQUIRES_PUBLIC_MEMORY",
                    "Numa memória que não é pública, os comentários são sempre só para participantes.");
        }
        if (parent != null && !parent.isPublic()) {
            throw new BusinessRuleException("REPLY_PUBLIC_REQUIRES_PUBLIC_COMMENT",
                    "A resposta a um comentário restrito também é só para participantes.");
        }
        return CommentVisibility.PUBLIC;
    }

    private static void requireAuthor(Comment comment, UUID userId) {
        if (!comment.isAuthoredBy(userId)) {
            throw new ForbiddenException("COMMENT_AUTHOR_ONLY", "Só quem escreveu o comentário pode fazer isso.");
        }
    }

    /** @menções do texto que apontam para participantes do evento. */
    private List<Mention> mentions(EventContext event, String text) {
        if (text == null) return List.of();
        List<MentionParser.Candidate> candidates = MentionParser.find(text);
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<String, UUID> participants = people.participantsByUsername(event.eventId(),
                candidates.stream().map(MentionParser.Candidate::username).collect(Collectors.toSet()));
        return candidates.stream()
                .filter(c -> participants.containsKey(c.username()))
                .map(c -> new Mention(participants.get(c.username()), c.start(), c.length()))
                .toList();
    }

    private Long replyCount(VisibleComment visible) {
        Comment comment = visible.comment();
        if (comment.isReply()) {
            return null;
        }
        List<IdCount> counts = visible.memory().event().isParticipant()
                ? comments.countReplies(List.of(comment.getId()))
                : comments.countReplies(List.of(comment.getId()), CommentVisibility.PUBLIC);
        return counts.isEmpty() ? 0L : counts.getFirst().getTotal();
    }

    private static Pageable chronological(int page, int size) {
        Pageable requested = PageResponse.request(page, size);
        return PageRequest.of(requested.getPageNumber(), requested.getPageSize(), CHRONOLOGICAL);
    }

    private static Map<UUID, Long> toMap(List<IdCount> counts) {
        return counts.stream().collect(Collectors.toMap(IdCount::getId, IdCount::getTotal));
    }

    static NotFoundException notFound() {
        return new NotFoundException("COMMENT_NOT_FOUND", "Comentário não encontrado.");
    }

    // ------------------------------------------------------------------ visões

    private Page<CommentView> toViews(Page<Comment> page, UUID viewerId,
                                      java.util.function.Function<Comment, Long> replyCount) {
        Map<UUID, PersonView> persons = people.summaries(viewerId, peopleIn(page.getContent()));
        Map<UUID, MediaView> audios = media.audiosForComments(
                page.getContent().stream().map(Comment::getId).toList());
        return page.map(c -> render(c, persons, audios.get(c.getId()), replyCount.apply(c), viewerId));
    }

    private CommentView toView(Comment comment, Long replyCount, UUID viewerId) {
        Map<UUID, PersonView> persons = people.summaries(viewerId, peopleIn(List.of(comment)));
        MediaView audio = media.audiosForComments(List.of(comment.getId())).get(comment.getId());
        return render(comment, persons, audio, replyCount, viewerId);
    }

    private static Set<UUID> peopleIn(Collection<Comment> list) {
        Set<UUID> ids = new HashSet<>();
        for (Comment c : list) {
            ids.add(c.getAuthorId());
            c.getMentions().forEach(m -> ids.add(m.getUserId()));
        }
        return ids;
    }

    /**
     * Monta o texto com o @usuário atual de cada pessoa mencionada e recalcula as posições das menções nesse
     * texto. Conta excluída aparece como "Usuário excluído"; perfil oculto para quem consulta, como "Perfil
     * privado".
     */
    private static CommentView render(Comment c, Map<UUID, PersonView> persons, MediaView audio,
                                      Long replyCount, UUID viewerId) {
        String stored = c.getText();
        if (stored == null) {
            return new CommentView(c.getId(), c.getMemoryId(), c.getParentId(), persons.get(c.getAuthorId()), null,
                    List.of(), audio, c.getVisibility(), replyCount, c.getCreatedAt(), c.getEditedAt(),
                    viewerId != null && c.isAuthoredBy(viewerId));
        }
        StringBuilder text = new StringBuilder(stored.length() + 16);
        List<MentionView> mentions = new ArrayList<>();
        int cursor = 0;
        for (Mention m : c.getMentions().stream()
                .sorted(java.util.Comparator.comparingInt(Mention::getStart)).toList()) {
            if (m.getStart() < cursor || m.getEnd() > stored.length()) {
                continue;
            }
            PersonView person = persons.get(m.getUserId());
            String label = person == null || person.deleted() ? DELETED_USER_LABEL
                    : person.profileHidden() ? HIDDEN_USER_LABEL
                    : "@" + person.username();
            text.append(stored, cursor, m.getStart());
            mentions.add(new MentionView(text.length(), label.length(), person));
            text.append(label);
            cursor = m.getEnd();
        }
        text.append(stored, cursor, stored.length());
        return new CommentView(c.getId(), c.getMemoryId(), c.getParentId(), persons.get(c.getAuthorId()),
                text.toString(), List.copyOf(mentions), audio, c.getVisibility(), replyCount, c.getCreatedAt(),
                c.getEditedAt(), viewerId != null && c.isAuthoredBy(viewerId));
    }

    // ------------------------------------------------------------------ contratos

    private record VisibleComment(Comment comment, MemoryAccess.Visible memory) {
    }

    /** Escrita e edição. Na edição, {@code null} mantém o valor atual. */
    public record WriteCommand(String text, CommentVisibility visibility, UUID audioId, boolean removeAudio) {
    }

    /**
     * @param replyCount respostas que quem consulta pode ver (só em comentário principal; {@code null} em resposta)
     */
    public record CommentView(UUID id, UUID memoryId, UUID parentId, PersonView author, String text,
                              List<MentionView> mentions, MediaView audio, CommentVisibility visibility, Long replyCount,
                              Instant createdAt, Instant editedAt, boolean mine) {
    }

    /**
     * Trecho do {@code text} (já com o @usuário atual) que é uma menção. Posições em unidades UTF-16.
     */
    public record MentionView(int start, int length, PersonView person) {
    }
}
