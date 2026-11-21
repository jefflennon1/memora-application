package br.com.memora.interaction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.memora.interaction.domain.MentionParser.Candidate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MentionParserTest {

    @Test
    void findsMentionsWithPositions() {
        String text = "Oi @Bia.Lima e @joao_2, olha isso!";
        List<Candidate> found = MentionParser.find(text);

        assertThat(found).containsExactly(
                new Candidate("bia.lima", 3, 9),
                new Candidate("joao_2", 15, 7));
        assertThat(text.substring(3, 12)).isEqualTo("@Bia.Lima");
    }

    @Test
    void trailingPunctuationIsNotPartOfTheUsername() {
        assertThat(MentionParser.find("Valeu, @bia.lima.")).containsExactly(new Candidate("bia.lima", 7, 9));
        assertThat(MentionParser.find("(@ana)")).containsExactly(new Candidate("ana", 1, 4));
    }

    @Test
    void positionsAreUtf16UnitsLikeJavaScript() {
        // "😀" ocupa 2 unidades UTF-16.
        assertThat(MentionParser.find("😀 @ana")).containsExactly(new Candidate("ana", 3, 4));
    }

    @Test
    void ignoresThingsThatAreNotMentions() {
        assertThat(MentionParser.find("escreva para ana@example.com")).isEmpty();
        assertThat(MentionParser.find("@abc")).isNotEmpty();
        assertThat(MentionParser.find("@ab")).isEmpty();
        assertThat(MentionParser.find("@a")).isEmpty();
        assertThat(MentionParser.find("@" + "a".repeat(31))).isEmpty();
        assertThat(MentionParser.find("@ana..lima")).isEmpty();
        assertThat(MentionParser.find("@@ana")).isEmpty();
        assertThat(MentionParser.find("sem menções")).isEmpty();
        assertThat(MentionParser.find(null)).isEmpty();
    }
}
