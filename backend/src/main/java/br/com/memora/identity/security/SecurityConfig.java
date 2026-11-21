package br.com.memora.identity.security;

import br.com.memora.shared.config.MemoraProperties;
import br.com.memora.shared.security.ProblemDetailsSecurityHandlers;
import br.com.memora.shared.security.SpaCsrfTokenRequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Segurança HTTP da Memora.
 *
 * <ul>
 *   <li><b>Login:</b> @usuário e senha locais ou OAuth2 Authorization Code + OpenID Connect com Google.</li>
 *   <li><b>Sessão:</b> sessão do Spring Security no cookie {@code MEMORA_SESSION} (HttpOnly; Secure em HTTPS),
 *       com troca do ID de sessão no login (proteção contra session fixation).</li>
 *   <li><b>CSRF:</b> cookie {@code XSRF-TOKEN} + cabeçalho {@code X-XSRF-TOKEN} em operações que alteram estado.</li>
 *   <li><b>CORS:</b> apenas a origem configurada do frontend, com credenciais.</li>
 *   <li><b>REST:</b> requisições sem sessão recebem 401 em Problem Details, nunca redirecionamento HTML.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    public static final String SESSION_COOKIE = "MEMORA_SESSION";
    public static final String LOGOUT_URL = "/api/v1/auth/logout";

    static final String[] PUBLIC_DOCS = {"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"};

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http,
                                    ProblemDetailsSecurityHandlers problemHandlers,
                                    LoginHandlers loginHandlers,
                                    MemoraOidcUserService oidcUserService,
                                    ClientRegistrationRepository registrations,
                                    GoogleOAuthProperties google,
                                    MemoraProperties properties,
                                    ObjectMapper objectMapper) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        if (properties.security().hasCsrfCookieDomain()) {
            String domain = properties.security().csrfCookieDomain();
            csrfRepository.setCookieCustomizer(cookie -> cookie.domain(domain));
        }

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(PUBLIC_DOCS).permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/username-availability").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/event-categories").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/*/photo", "/api/v1/events/*/cover").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/invites/received").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/invites/*", "/api/v1/invites/*/background").permitAll()
                        // "Meus eventos" exige sessão; a busca pública e a consulta de um evento são abertas
                        // (a visibilidade de eventos privados é decidida no serviço, com 404 para quem não participa).
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/mine").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/stream").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/public", "/api/v1/events/*").permitAll()
                        // Mural, memória, arquivos, comentários e reações de evento público podem ser vistos sem
                        // sessão; os serviços decidem o que cada pessoa (ou visitante anônimo) pode ver.
                        .requestMatchers(HttpMethod.GET, "/api/v1/events/*/memories", "/api/v1/memories/*",
                                "/api/v1/media/*", "/api/v1/memories/*/comments", "/api/v1/comments/*",
                                "/api/v1/comments/*/replies", "/api/v1/comments/*/audio",
                                "/api/v1/memories/*/reactions/summary").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauth -> oauth
                        // Página de login "própria" = início do fluxo Google; desativa a página HTML padrão.
                        .loginPage("/oauth2/authorization/" + OAuthClientConfig.GOOGLE)
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestResolver(new RememberingAuthorizationRequestResolver(registrations)))
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                        .successHandler(loginHandlers)
                        .failureHandler(loginHandlers))
                .addFilterBefore(new GoogleLoginNotConfiguredFilter(google, objectMapper),
                        OAuth2AuthorizationRequestRedirectFilter.class)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .cors(cors -> {})
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers))
                .logout(logout -> logout
                        .logoutUrl(LOGOUT_URL)
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies(SESSION_COOKIE))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    AuthenticationManager localAuthenticationManager(LocalAuthenticationProvider provider) {
        return new ProviderManager(provider);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(MemoraProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(properties.frontend().normalizedOrigin()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Accept", "X-XSRF-TOKEN", "Last-Event-ID", "Range"));
        config.setExposedHeaders(List.of("Location", "Accept-Ranges", "Content-Range"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
