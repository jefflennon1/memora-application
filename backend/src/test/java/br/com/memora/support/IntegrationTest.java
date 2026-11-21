package br.com.memora.support;

import br.com.memora.TestcontainersConfiguration;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Teste de integração: aplicação completa + PostgreSQL real (Testcontainers) + perfil {@code test}.
 * O banco é compartilhado entre as classes; cada teste cria seus próprios dados (subs e @usuários aleatórios).
 * Os arquivos ficam num armazenamento em memória ({@link InMemoryObjectStorage}).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestUsers.class, InMemoryObjectStorage.Config.class})
public @interface IntegrationTest {
}
