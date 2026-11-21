package br.com.memora.identity.security;

import br.com.memora.identity.domain.LocalCredentialRepository;
import br.com.memora.identity.domain.LocalCredential;
import br.com.memora.identity.domain.User;
import br.com.memora.identity.domain.UserRepository;
import br.com.memora.profile.domain.Username;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Autentica @usuário e senha sem revelar se o nome existe. */
@Component
public class LocalAuthenticationProvider implements AuthenticationProvider {

    private final UserRepository users;
    private final LocalCredentialRepository credentials;
    private final PasswordEncoder passwordEncoder;
    private final String dummyHash;

    public LocalAuthenticationProvider(UserRepository users, LocalCredentialRepository credentials,
                                       PasswordEncoder passwordEncoder) {
        this.users = users;
        this.credentials = credentials;
        this.passwordEncoder = passwordEncoder;
        this.dummyHash = passwordEncoder.encode("memora-dummy-password-never-used");
    }

    @Override
    @Transactional(readOnly = true)
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = Username.normalize(authentication.getName());
        String rawPassword = authentication.getCredentials() == null ? "" : authentication.getCredentials().toString();
        User user = users.findByUsername(username).orElse(null);
        Optional<LocalCredential> credential = user == null ? Optional.empty() : credentials.findByUserId(user.getId());
        String hash = credential.map(LocalCredential::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(rawPassword, hash);
        if (user == null || !matches || credential.isEmpty()) {
            throw new BadCredentialsException("Invalid username or password");
        }
        if (user.isDisabled()) {
            throw new DisabledException("Account disabled");
        }
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        return UsernamePasswordAuthenticationToken.authenticated(
                new LocalMemoraPrincipal(user.getId()), null, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
