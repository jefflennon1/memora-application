package br.com.memora.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * Tratamento de CSRF para frontend SPA (padrão recomendado pela documentação do Spring Security).
 *
 * <p>O token é publicado no cookie {@code XSRF-TOKEN} (legível por JavaScript) e o frontend o devolve
 * no cabeçalho {@code X-XSRF-TOKEN} em toda requisição que altera estado (POST, PUT, PATCH, DELETE).
 * Valores vindos do cabeçalho são comparados diretamente; valores renderizados em formulários usam a
 * proteção XOR contra BREACH.
 */
public final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        xor.handle(request, response, csrfToken);
        // Força o carregamento do token para que o cookie XSRF-TOKEN seja emitido.
        csrfToken.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String header = request.getHeader(csrfToken.getHeaderName());
        return (StringUtils.hasText(header) ? plain : xor).resolveCsrfTokenValue(request, csrfToken);
    }
}
