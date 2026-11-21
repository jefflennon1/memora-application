package br.com.memora.interaction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.memora.shared.error.ValidationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class EmojiTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "😀", "🎉", "🙏", "👍🏽", "👨‍👩‍👧‍👦", "🧑🏻‍🤝‍🧑🏿", "🇧🇷", "🏳️‍🌈", "🏴󠁧󠁢󠁳󠁣󠁴󠁿", "1️⃣", "#️⃣", "❤️", "☺️"
    })
    void acceptsASingleEmoji(String emoji) {
        assertThat(Emoji.normalize(emoji)).isEqualTo(emoji);
    }

    @ParameterizedTest
    @CsvSource({
            // Sem o seletor de variação, um emoji que aparece como texto por padrão ganha U+FE0F.
            "❤, ❤️",
            "☺, ☺️",
            // Emoji que já aparece como desenho não precisa do seletor.
            "😀️, 😀",
            "' 🎉 ', 🎉"
    })
    void normalizesVariationSelector(String input, String expected) {
        assertThat(Emoji.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "a", "1", "#", "ok", "😀😀", "❤️❤️", "😀a", "🇧", "😀́", ":)", "<3"})
    void rejectsAnythingButOneEmoji(String value) {
        assertThatThrownBy(() -> Emoji.normalize(value))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "INVALID_EMOJI");
    }

    @org.junit.jupiter.api.Test
    void rejectsNull() {
        assertThatThrownBy(() -> Emoji.normalize(null)).isInstanceOf(ValidationException.class);
    }
}
