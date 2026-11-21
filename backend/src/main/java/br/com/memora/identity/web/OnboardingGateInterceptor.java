package br.com.memora.identity.web;

import br.com.memora.identity.application.AccountDisabledException;
import br.com.memora.identity.application.CurrentAccountService;
import br.com.memora.identity.security.MemoraPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Bloqueio de onboarding: usuários autenticados só usam a plataforma após concluir o cadastro inicial.
 *
 * <ul>
 *   <li>Conta suspensa/excluída: 403 {@code ACCOUNT_DISABLED} em qualquer endpoint.</li>
 *   <li>Onboarding pendente: 403 {@code ONBOARDING_REQUIRED}, exceto endpoints com
 *       {@link AllowIncompleteOnboarding}.</li>
 *   <li>Requisições anônimas não são afetadas (o Spring Security já decidiu se são permitidas).</li>
 * </ul>
 */
@Component
public class OnboardingGateInterceptor implements HandlerInterceptor {

    private final CurrentAccountService accounts;

    public OnboardingGateInterceptor(CurrentAccountService accounts) {
        this.accounts = accounts;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof MemoraPrincipal principal)) {
            return true;
        }
        boolean allowIncomplete = method.hasMethodAnnotation(AllowIncompleteOnboarding.class)
                || method.getBeanType().isAnnotationPresent(AllowIncompleteOnboarding.class);
        if (allowIncomplete) {
            if (accounts.statusOf(principal.userId()).isDisabled()) {
                throw new AccountDisabledException();
            }
            return true;
        }
        accounts.requireActive(principal.userId());
        return true;
    }
}
