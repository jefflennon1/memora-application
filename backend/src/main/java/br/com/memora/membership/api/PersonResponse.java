package br.com.memora.membership.api;

import br.com.memora.membership.application.PeopleDirectory.PersonView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Pessoa exibida junto a um conteúdo (organizador, participante, autor). */
@Schema(name = "Person", description = "Quando `profileHidden` é true (perfil privado sem evento em comum, ou conta "
        + "excluída), nome, foto e @usuário não são enviados.")
public record PersonResponse(
        UUID id,
        String username,
        String displayName,
        String photoUrl,
        @Schema(description = "Perfil oculto para quem consulta") boolean profileHidden) {

    public static PersonResponse from(PersonView view) {
        return view == null ? null
                : new PersonResponse(view.id(), view.username(), view.displayName(), view.photoUrl(),
                        view.profileHidden());
    }
}
