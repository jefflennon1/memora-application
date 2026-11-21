package br.com.memora.shared.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchTextTest {

    @Test
    void removesAccentsCaseAndExtraSpaces() {
        assertThat(SearchText.normalize("  Chá de   Fraldas do LIAM ")).isEqualTo("cha de fraldas do liam");
        assertThat(SearchText.normalize("São João del-Rei")).isEqualTo("sao joao del-rei");
    }

    @Test
    void blankBecomesNull() {
        assertThat(SearchText.normalize(null)).isNull();
        assertThat(SearchText.normalize("   ")).isNull();
    }

    @Test
    void containsPatternEscapesLikeWildcards() {
        assertThat(SearchText.containsPattern("50%_off\\")).isEqualTo("%50\\%\\_off\\\\%");
    }
}
