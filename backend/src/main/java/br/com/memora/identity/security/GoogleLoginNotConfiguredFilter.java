package br.com.memora.identity.security;

import br.com.memora.shared.error.ApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Quando as credenciais do Google não estão configuradas, responde às rotas de login com HTTP 503 e uma
 * explicação objetiva, em vez de um erro genérico ou de um redirecionamento que falharia no Google.
 */
class GoogleLoginNotConfiguredFilter extends OncePerRequestFilter {

    private final GoogleOAuthProperties google;
    private final ObjectMapper objectMapper;

    GoogleLoginNotConfiguredFilter(GoogleOAuthProperties google, ObjectMapper objectMapper) {
        this.google = google;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return google.isConfigured()
                || !(path.startsWith("/oauth2/authorization/") || path.startsWith("/login/oauth2/code/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ProblemDetail pd = ApiExceptionHandler.problem(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_LOGIN_NOT_CONFIGURED",
                "O login com Google não está configurado neste servidor. "
                        + "Defina as variáveis GOOGLE_CLIENT_ID e GOOGLE_CLIENT_SECRET e reinicie a API.");
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), pd);
    }
}
