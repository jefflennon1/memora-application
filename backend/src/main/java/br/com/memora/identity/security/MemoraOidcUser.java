package br.com.memora.identity.security;

import java.util.Collection;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Usuário OIDC autenticado e já associado à conta interna da Memora.
 * {@link #getName()} devolve o UUID da conta, nunca o e-mail ou o {@code sub} do provedor.
 */
public class MemoraOidcUser extends DefaultOidcUser implements MemoraPrincipal {

    private static final long serialVersionUID = 1L;

    private final UUID userId;

    public MemoraOidcUser(UUID userId, Collection<? extends GrantedAuthority> authorities,
                          OidcIdToken idToken, OidcUserInfo userInfo) {
        super(authorities, idToken, userInfo);
        this.userId = userId;
    }

    public static MemoraOidcUser of(UUID userId, OidcUser oidcUser) {
        return new MemoraOidcUser(userId, oidcUser.getAuthorities(), oidcUser.getIdToken(), oidcUser.getUserInfo());
    }

    @Override
    public UUID userId() {
        return userId;
    }

    @Override
    public String getName() {
        return userId.toString();
    }
}
