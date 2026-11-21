package br.com.memora.identity.security;

import br.com.memora.identity.application.AccountDisabledException;
import br.com.memora.identity.application.ExternalIdentityClaims;
import br.com.memora.identity.application.SignInResult;
import br.com.memora.identity.application.SignInService;
import br.com.memora.identity.domain.IdentityProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Etapa final do login OIDC: depois que o Spring Security validou o ID Token (assinatura, emissor,
 * audiência, expiração e nonce), associa a identidade à conta interna da Memora.
 */
@Component
public class MemoraOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    public static final String ACCOUNT_DISABLED_ERROR = "account_disabled";

    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;
    private final SignInService signInService;

    @Autowired
    public MemoraOidcUserService(SignInService signInService) {
        this(new OidcUserService(), signInService);
    }

    MemoraOidcUserService(OAuth2UserService<OidcUserRequest, OidcUser> delegate, SignInService signInService) {
        this.delegate = delegate;
        this.signInService = signInService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser oidcUser = delegate.loadUser(request);
        IdentityProvider provider =
                IdentityProvider.fromRegistrationId(request.getClientRegistration().getRegistrationId());
        ExternalIdentityClaims claims = new ExternalIdentityClaims(
                provider,
                oidcUser.getSubject(),
                oidcUser.getEmail(),
                oidcUser.getEmailVerified(),
                oidcUser.getFullName(),
                oidcUser.getPicture());
        try {
            SignInResult result = signInService.signIn(claims);
            return MemoraOidcUser.of(result.userId(), oidcUser);
        } catch (AccountDisabledException e) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(ACCOUNT_DISABLED_ERROR, "Conta desativada.", null), e);
        }
    }
}
