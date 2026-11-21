package br.com.memora.shared.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio único da aplicação. Regras dependentes de data (ex.: evento não pode iniciar no passado)
 * usam este {@link Clock}, o que permite testá-las de forma determinística.
 *
 * <p>O relógio avança em microssegundos, a mesma precisão do {@code TIMESTAMPTZ} do PostgreSQL: assim um
 * horário devolvido logo após gravar é idêntico ao lido depois do banco (no Windows o relógio do sistema tem
 * precisão de 100 ns).
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
