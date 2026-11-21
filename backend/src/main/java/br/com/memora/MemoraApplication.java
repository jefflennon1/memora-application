package br.com.memora;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Memora — Momentos passam. Memórias ficam.
 *
 * <p>Monólito modular. Cada subpacote direto de {@code br.com.memora} representa um módulo de domínio
 * (identity, profile, event, membership, memory, interaction, notification, moderation) e
 * {@code shared} concentra apenas o que é genuinamente transversal.
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} é excluída porque a Memora possui seu próprio provedor
 * de autenticação local, além do login OpenID Connect, e não usa o usuário padrão do Spring Boot.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class MemoraApplication {

    public static void main(String[] args) {
        SpringApplication.run(MemoraApplication.class, args);
    }
}
