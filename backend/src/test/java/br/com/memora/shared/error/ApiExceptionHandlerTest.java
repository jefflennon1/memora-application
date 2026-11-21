package br.com.memora.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void domainExceptionBecomesProblemDetailWithStableCode() {
        ProblemDetail pd = handler.handleDomain(
                new BusinessRuleException("MURAL_CLOSED", "O mural deste evento está fechado."));

        assertThat(pd.getStatus()).isEqualTo(422);
        assertThat(pd.getDetail()).isEqualTo("O mural deste evento está fechado.");
        assertThat(pd.getType()).hasToString("https://memora.example/problems/mural-closed");
        assertThat(pd.getProperties()).containsEntry("code", "MURAL_CLOSED");
    }

    @Test
    void unexpectedErrorsHideInternalMessages() {
        ProblemDetail pd = handler.handleUnexpected(new IllegalStateException("internal implementation detail"));

        assertThat(pd.getStatus()).isEqualTo(500);
        assertThat(pd.getDetail()).doesNotContain("implementation detail");
        assertThat(pd.getProperties()).containsEntry("code", "INTERNAL_ERROR");
    }
}
