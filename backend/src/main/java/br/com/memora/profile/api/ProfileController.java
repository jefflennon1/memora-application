package br.com.memora.profile.api;

import br.com.memora.identity.domain.IdentityProvider;
import br.com.memora.identity.domain.ProfilePrivacy;
import br.com.memora.identity.domain.UserStatus;
import br.com.memora.identity.security.MemoraPrincipal;
import br.com.memora.identity.web.AllowIncompleteOnboarding;
import br.com.memora.profile.application.ProfileService;
import br.com.memora.profile.application.ProfileService.MyProfile;
import br.com.memora.shared.config.OpenApiConfig;
import br.com.memora.shared.web.InlineImageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Conta e perfil", description = "Cadastro inicial (onboarding) e perfil do usuário autenticado.")
@SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
public class ProfileController {

    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/me")
    @AllowIncompleteOnboarding
    @Operation(summary = "Consulta a conta autenticada",
            description = "Primeira chamada do frontend após o login. Se `onboardingCompleted` for `false`, "
                    + "o frontend deve exibir o cadastro inicial; `displayName` e `photoUrl` trazem as sugestões "
                    + "vindas do Google.")
    public MeResponse me(@AuthenticationPrincipal MemoraPrincipal principal) {
        return MeResponse.from(profiles.me(principal.userId()));
    }

    @PatchMapping("/me/onboarding")
    @AllowIncompleteOnboarding
    @Operation(summary = "Conclui o cadastro inicial",
            description = "Confirma o nome, decide sobre a foto sugerida e define o @usuário (único). "
                    + "Só pode ser feito uma vez; depois use `PATCH /users/me`.")
    public MeResponse completeOnboarding(@AuthenticationPrincipal MemoraPrincipal principal,
                                         @Valid @RequestBody OnboardingRequest body) {
        return MeResponse.from(profiles.completeOnboarding(principal.userId(), new ProfileService.OnboardingCommand(
                body.username(), body.displayName(),
                body.keepSuggestedPhoto() == null || body.keepSuggestedPhoto(),
                body.profilePrivacy())));
    }

    @PatchMapping("/me")
    @Operation(summary = "Atualiza o próprio perfil",
            description = "Altera somente os campos enviados. Exige cadastro inicial concluído.")
    public MeResponse update(@AuthenticationPrincipal MemoraPrincipal principal,
                             @Valid @RequestBody ProfileUpdateRequest body) {
        return MeResponse.from(profiles.update(principal.userId(), new ProfileService.ProfileUpdateCommand(
                body.username(), body.displayName(),
                Boolean.TRUE.equals(body.removePhoto()), body.profilePrivacy())));
    }

    @PutMapping(path = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Insere ou substitui a própria foto de perfil",
            description = "Campo multipart `file`. Aceita JPEG, PNG, WebP ou GIF; o tipo real é validado pelo conteúdo.")
    public MeResponse uploadPhoto(@AuthenticationPrincipal MemoraPrincipal principal,
                                  @RequestPart("file") MultipartFile file) throws IOException {
        return MeResponse.from(profiles.uploadPhoto(principal.userId(), file.getBytes()));
    }

    @DeleteMapping("/me/photo")
    @Operation(summary = "Remove a própria foto de perfil")
    public MeResponse removePhoto(@AuthenticationPrincipal MemoraPrincipal principal) {
        return MeResponse.from(profiles.removePhoto(principal.userId()));
    }

    @GetMapping("/{id}/photo")
    @AllowIncompleteOnboarding
    @Operation(summary = "Visualiza uma foto de perfil",
            description = "Respeita a privacidade do perfil. Perfis privados devolvem 404 para quem não pode vê-los.")
    public ResponseEntity<InputStreamResource> photo(@AuthenticationPrincipal MemoraPrincipal principal,
                                                     @PathVariable UUID id, WebRequest request) throws IOException {
        return InlineImageResponse.of(profiles.openPhoto(id, principal == null ? null : principal.userId()), request);
    }

    @GetMapping("/username-availability")
    @AllowIncompleteOnboarding
    @Operation(summary = "Verifica se um @usuário está disponível",
            description = "Útil durante o cadastro inicial. O valor é normalizado (sem \"@\", em minúsculas).")
    public UsernameAvailabilityResponse availability(
            @AuthenticationPrincipal MemoraPrincipal principal,
            @Parameter(example = "ana.souza") @RequestParam String username) {
        var result = profiles.availability(principal.userId(), username);
        return new UsernameAvailabilityResponse(result.username(), result.available(), result.reason());
    }

    // ---------------------------------------------------------------- DTOs

    @Schema(name = "OnboardingRequest")
    public record OnboardingRequest(
            @Schema(description = "@usuário desejado (o \"@\" é opcional)", example = "ana.souza")
            @NotBlank @Size(max = 31) String username,
            @Schema(description = "Nome exibido", example = "Ana Souza")
            @NotBlank @Size(max = 80) String displayName,
            @Schema(description = "Mantém a foto sugerida pelo Google (padrão: true). `false` remove a foto.",
                    example = "true")
            Boolean keepSuggestedPhoto,
            @Schema(description = "Privacidade do perfil (padrão: PUBLIC)")
            ProfilePrivacy profilePrivacy) {
    }

    @Schema(name = "ProfileUpdateRequest")
    public record ProfileUpdateRequest(
            @Schema(example = "ana.souza") @Size(max = 31) String username,
            @Schema(example = "Ana Souza") @Size(max = 80) String displayName,
            @Schema(description = "`true` remove a foto do perfil") Boolean removePhoto,
            ProfilePrivacy profilePrivacy) {
    }

    @Schema(name = "Me")
    public record MeResponse(
            UUID id,
            @Schema(description = "@usuário (ausente até concluir o onboarding)") String username,
            String displayName,
            String photoUrl,
            @Schema(description = "Telefone privado; visível somente ao próprio usuário") String phone,
            ProfilePrivacy profilePrivacy,
            UserStatus status,
            boolean onboardingCompleted,
            Instant createdAt,
            Instant onboardingCompletedAt,
            boolean passwordLogin,
            @Schema(description = "Contas de login vinculadas (visível somente ao próprio usuário)")
            List<IdentityResponse> identities) {

        static MeResponse from(MyProfile p) {
            return new MeResponse(p.id(), p.username(), p.displayName(), p.photoUrl(), p.phone(), p.profilePrivacy(),
                    p.status(), p.onboardingCompleted(), p.createdAt(), p.onboardingCompletedAt(),
                    p.passwordLogin(),
                    p.identities().stream()
                            .map(i -> new IdentityResponse(i.provider(), i.email(), i.emailVerified(), i.linkedAt()))
                            .toList());
        }
    }

    @Schema(name = "LinkedIdentity")
    public record IdentityResponse(IdentityProvider provider, String email, Boolean emailVerified, Instant linkedAt) {
    }

    @Schema(name = "UsernameAvailability")
    public record UsernameAvailabilityResponse(
            String username,
            boolean available,
            @Schema(description = "INVALID_USERNAME, USERNAME_RESERVED ou USERNAME_TAKEN quando indisponível")
            String reason) {
    }
}
