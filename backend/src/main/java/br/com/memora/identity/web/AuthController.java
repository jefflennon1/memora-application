package br.com.memora.identity.web;

import br.com.memora.identity.application.AccountDisabledException;
import br.com.memora.identity.application.InvalidCredentialsException;
import br.com.memora.identity.application.LocalAccountService;
import br.com.memora.identity.application.LocalAccountService.RegisteredAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação local", description = "Cadastro simples e login por @usuário e senha.")
public class AuthController {

    private final LocalAccountService accounts;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contexts = new HttpSessionSecurityContextRepository();
    private final SessionAuthenticationStrategy sessions = new ChangeSessionIdAuthenticationStrategy();

    public AuthController(LocalAccountService accounts, AuthenticationManager authenticationManager) {
        this.accounts = accounts;
        this.authenticationManager = authenticationManager;
    }

    @GetMapping("/username-availability")
    @Operation(summary = "Verifica se um @usuário está disponível para cadastro")
    public UsernameAvailabilityResponse availability(@RequestParam String username) {
        var result = accounts.availability(username);
        return new UsernameAvailabilityResponse(result.username(), result.available(), result.reason());
    }

    @PostMapping("/register")
    @Operation(summary = "Cria uma conta local e inicia a sessão",
            description = "O telefone é privado e a senha é armazenada somente como hash adaptativo.")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest body,
                                         HttpServletRequest request, HttpServletResponse response) {
        RegisteredAccount account = accounts.register(new LocalAccountService.RegisterCommand(
                body.username(), body.password(), body.passwordConfirmation(), body.phone()));
        establishSession(account.username(), body.password(), request, response);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    @Operation(summary = "Entra com @usuário e senha")
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest body,
                                      HttpServletRequest request, HttpServletResponse response) {
        establishSession(body.username(), body.password(), request, response);
        return ResponseEntity.noContent().build();
    }

    private void establishSession(String username, String password,
                                  HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        } catch (DisabledException ex) {
            throw new AccountDisabledException();
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException();
        }
        sessions.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
    }

    @Schema(name = "LocalLoginRequest")
    public record LoginRequest(
            @NotBlank @Size(max = 31) String username,
            @NotBlank @Size(max = 72) String password) {
    }

    @Schema(name = "LocalRegisterRequest")
    public record RegisterRequest(
            @NotBlank @Size(max = 31) String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(min = 8, max = 72) String passwordConfirmation,
            @NotBlank @Size(max = 30) String phone) {
    }

    @Schema(name = "PublicUsernameAvailability")
    public record UsernameAvailabilityResponse(String username, boolean available, String reason) {
    }
}
