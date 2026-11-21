package br.com.memora.identity.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * Valida o destino do navegador após o login, evitando open redirect.
 *
 * <p>Só são aceitos <strong>caminhos relativos</strong> do frontend (ex.: {@code /invites/abc}),
 * sempre concatenados à origem configurada. URLs absolutas, protocolo-relativas ({@code //host}),
 * barras invertidas, espaços e caracteres de controle são recusados.
 */
public final class LoginRedirectPolicy {

    public static final String PARAMETER = "redirect_to";
    static final int MAX_LENGTH = 512;

    private LoginRedirectPolicy() {
    }

    public static Optional<String> sanitizePath(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        if (!raw.startsWith("/") || raw.startsWith("//") || raw.indexOf('\\') >= 0) {
            return Optional.empty();
        }
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isISOControl(c) || Character.isWhitespace(c)) {
                return Optional.empty();
            }
        }
        try {
            URI uri = new URI(raw);
            if (uri.getScheme() != null || uri.getRawAuthority() != null) {
                return Optional.empty();
            }
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        return Optional.of(raw);
    }
}
