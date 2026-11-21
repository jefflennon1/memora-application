package br.com.memora.membership.application;

import br.com.memora.identity.domain.ProfilePrivacy;
import br.com.memora.identity.domain.User;
import br.com.memora.identity.domain.UserRepository;
import br.com.memora.identity.domain.UserStatus;
import br.com.memora.membership.domain.EventParticipantRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resumo de pessoas (autor, organizador, participante) respeitando a privacidade do perfil.
 *
 * <p>Perfil {@code PUBLIC}: nome, foto e @usuário visíveis a qualquer pessoa. Perfil {@code PRIVATE}: só para
 * quem participa de algum evento em comum (e para a própria pessoa). Para os demais, e para contas excluídas,
 * o resumo sai com {@code profileHidden = true} e sem dados pessoais.
 */
@Service
public class PeopleDirectory {

    private final UserRepository users;
    private final EventParticipantRepository participants;

    public PeopleDirectory(UserRepository users, EventParticipantRepository participants) {
        this.users = users;
        this.participants = participants;
    }

    /** @param viewerId quem vai ver os resumos; {@code null} para visitante anônimo */
    @Transactional(readOnly = true)
    public PersonView summary(UUID viewerId, UUID userId) {
        return summaries(viewerId, List.of(userId)).get(userId);
    }

    /** Resumos de várias pessoas de uma vez (duas consultas, independentemente da quantidade). */
    @Transactional(readOnly = true)
    public Map<UUID, PersonView> summaries(UUID viewerId, Collection<UUID> userIds) {
        Set<UUID> ids = Set.copyOf(userIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<User> found = users.findAllById(ids);
        Set<UUID> privateIds = found.stream()
                .filter(u -> u.getProfilePrivacy() == ProfilePrivacy.PRIVATE && !u.getId().equals(viewerId))
                .map(User::getId)
                .collect(Collectors.toSet());
        Set<UUID> sharing = viewerId == null || privateIds.isEmpty() ? Set.of()
                : participants.findUsersSharingAnEventWith(viewerId, privateIds);

        Map<UUID, PersonView> result = new HashMap<>();
        for (UUID id : ids) {
            result.put(id, PersonView.hidden(id));
        }
        for (User user : found) {
            boolean deleted = user.getStatus() == UserStatus.DELETED;
            boolean blockedByPrivacy = privateIds.contains(user.getId()) && !sharing.contains(user.getId());
            if (deleted) {
                result.put(user.getId(), PersonView.deleted(user.getId()));
            } else if (!blockedByPrivacy) {
                result.put(user.getId(), PersonView.of(user));
            }
        }
        return result;
    }

    /**
     * Resolve @usuários (já normalizados) para pessoas que participam do evento. Quem não existe, não participa
     * ou teve a conta excluída fica de fora: só se menciona quem está no evento.
     */
    @Transactional(readOnly = true)
    public Map<String, UUID> participantsByUsername(UUID eventId, Collection<String> usernames) {
        if (usernames.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> candidates = users.findByUsernameIn(Set.copyOf(usernames)).stream()
                .filter(u -> u.getStatus() != UserStatus.DELETED)
                .collect(Collectors.toMap(User::getId, User::getUsername));
        if (candidates.isEmpty()) {
            return Map.of();
        }
        Set<UUID> inEvent = participants.findParticipantsAmong(eventId, candidates.keySet());
        return candidates.entrySet().stream()
                .filter(e -> inEvent.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    }

    /**
     * Resumos sem aplicar a privacidade do perfil (contas excluídas continuam ocultas). Só para contextos em que
     * a própria pessoa se apresentou a quem consulta: o pedido de entrada que ela fez, ou o convite nominal que
     * a organização criou digitando o @usuário dela.
     */
    @Transactional(readOnly = true)
    public Map<UUID, PersonView> revealed(Collection<UUID> userIds) {
        Map<UUID, PersonView> result = new HashMap<>();
        for (UUID id : Set.copyOf(userIds)) {
            result.put(id, PersonView.hidden(id));
        }
        for (User user : users.findAllById(result.keySet())) {
            result.put(user.getId(), user.getStatus() == UserStatus.DELETED
                    ? PersonView.deleted(user.getId()) : PersonView.of(user));
        }
        return result;
    }

    /**
     * Pessoa como aparece para quem consulta.
     *
     * @param deleted conta excluída (também oculta). Uso interno, por exemplo para exibir "Usuário excluído" numa
     *                @menção; não é enviado no resumo público da pessoa.
     */
    public record PersonView(UUID id, String username, String displayName, String photoUrl, boolean profileHidden,
                             boolean deleted) {

        static PersonView of(User user) {
            return new PersonView(user.getId(), user.getUsername(), user.getDisplayName(), user.getPhotoUrl(),
                    false, false);
        }

        static PersonView hidden(UUID id) {
            return new PersonView(id, null, null, null, true, false);
        }

        static PersonView deleted(UUID id) {
            return new PersonView(id, null, null, null, true, true);
        }
    }
}
