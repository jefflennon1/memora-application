package br.com.memora.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.memora.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Sem credenciais Google a API inicia, mas o login informa exatamente o que falta (nada de sucesso inventado).
 */
@SpringBootTest(properties = {"memora.auth.google.client-id=", "memora.auth.google.client-secret="})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GoogleNotConfiguredIT {

    @Autowired
    MockMvc mvc;

    @Test
    void loginRespondsServiceUnavailableExplainingMissingCredentials() throws Exception {
        mvc.perform(get("/oauth2/authorization/google"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GOOGLE_LOGIN_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("GOOGLE_CLIENT_ID")));
        mvc.perform(get("/login/oauth2/code/google").param("code", "x").param("state", "y"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
