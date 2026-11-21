package br.com.memora.identity.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca endpoints que um usuário autenticado pode usar <em>antes</em> de concluir o onboarding
 * (consultar o próprio cadastro, verificar @usuário, concluir o cadastro).
 *
 * <p>Todos os demais endpoints autenticados exigem conta ativa: a regra é "negar por padrão".
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface AllowIncompleteOnboarding {
}
