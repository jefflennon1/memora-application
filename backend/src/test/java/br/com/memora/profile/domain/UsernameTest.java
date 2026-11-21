package br.com.memora.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.memora.shared.error.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UsernameTest {

    @Test
    void normalizesAtSignCaseAndSurroundingSpaces() {
        assertThat(Username.parse("  @Ana.Souza_1 ")).isEqualTo("ana.souza_1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "a1b", "ana.souza", "ana_souza", "a2345678901234567890123456789z"})
    void acceptsValidUsernames(String value) {
        assertThat(Username.problemWith(value)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", ".ana", "ana.", "_ana", "ana_", "ana..souza", "ana souza", "joão", "ana-souza",
            "a23456789012345678901234567890z", ""})
    void rejectsInvalidFormats(String value) {
        assertThat(Username.problemWith(value)).contains("INVALID_USERNAME");
    }

    @Test
    void rejectsReservedNames() {
        assertThatThrownBy(() -> Username.parse("@Memora"))
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).code())
                .isEqualTo("USERNAME_RESERVED");
    }
}
