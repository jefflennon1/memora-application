package br.com.memora.support;

import br.com.memora.identity.application.ExternalIdentityClaims;
import br.com.memora.identity.application.SignInResult;
import br.com.memora.identity.application.SignInService;
import br.com.memora.identity.domain.IdentityProvider;
import br.com.memora.profile.application.ProfileService;
import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;

/**
 * Fixtures de usuários fictícios para testes. Passa pelos mesmos serviços do login real (identidade OIDC
 * simulada), sem nenhum atalho de autenticação na aplicação.
 */
@TestComponent
public class TestUsers {

    private final SignInService signIn;
    private final ProfileService profiles;

    public TestUsers(SignInService signIn, ProfileService profiles) {
        this.signIn = signIn;
        this.profiles = profiles;
    }

    /** Primeiro login de uma identidade Google simulada: conta com onboarding pendente. */
    public TestUser pending() {
        String subject = "google-sub-" + UUID.randomUUID();
        SignInResult result = signIn.signIn(new ExternalIdentityClaims(IdentityProvider.GOOGLE, subject,
                subject + "@example.test", true, "Pessoa Teste", "https://lh3.googleusercontent.com/a/test"));
        return new TestUser(result.userId(), subject, null);
    }

    /** Conta com onboarding concluído e @usuário aleatório. */
    public TestUser active() {
        TestUser user = pending();
        String username = randomUsername();
        profiles.completeOnboarding(user.id(),
                new ProfileService.OnboardingCommand(username, "Pessoa Teste", true, null));
        return new TestUser(user.id(), user.subject(), username);
    }

    public static String randomUsername() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public record TestUser(UUID id, String subject, String username) {
    }
}
