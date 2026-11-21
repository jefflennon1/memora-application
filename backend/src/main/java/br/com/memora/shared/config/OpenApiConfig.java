package br.com.memora.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados da documentação OpenAPI / Swagger UI.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    public static final String SESSION_SCHEME = "sessionCookie";

    @Bean
    OpenAPI memoraOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Memora API")
                        .version("v1")
                        .description("""
                                **Momentos passam. Memórias ficam.**

                                API da Memora, rede social de memórias coletivas organizadas por eventos.

                                Autenticação: sessão web (cookie `MEMORA_SESSION`) estabelecida pelo login local
                                ou pelo Google.
                                Operações que alteram estado exigem o cabeçalho `X-XSRF-TOKEN` com o valor do cookie
                                `XSRF-TOKEN`. Erros seguem o formato Problem Details (RFC 9457)."""))
                .components(new Components()
                        .addSecuritySchemes(SESSION_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("MEMORA_SESSION")
                                .description("Cookie de sessão emitido após o login local ou com Google.")));
    }
}
