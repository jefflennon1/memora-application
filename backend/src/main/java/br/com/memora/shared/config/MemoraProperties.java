package br.com.memora.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Configurações próprias da Memora ({@code memora.*}).
 */
@Validated
@ConfigurationProperties(prefix = "memora")
public record MemoraProperties(@Valid @NotNull Frontend frontend, Security security) {

    public MemoraProperties {
        if (security == null) {
            security = new Security(null);
        }
    }

    /**
     * @param origin             origem do frontend autorizada no CORS (ex.: {@code http://localhost:3000})
     * @param postLoginRedirect  destino padrão do navegador após o login
     * @param loginErrorRedirect destino do navegador quando o login falha
     */
    public record Frontend(@NotBlank String origin, @NotBlank String postLoginRedirect,
                           @NotBlank String loginErrorRedirect) {

        /** Origem sem barra final, pronta para concatenar caminhos. */
        public String normalizedOrigin() {
            return origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin;
        }
    }

    /**
     * @param csrfCookieDomain domínio do cookie {@code XSRF-TOKEN}; vazio significa o host da API
     */
    public record Security(String csrfCookieDomain) {

        public boolean hasCsrfCookieDomain() {
            return StringUtils.hasText(csrfCookieDomain);
        }
    }
}
