package br.com.memora.interaction.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encontra possíveis @menções num texto: "@" seguido de algo com o formato de um @usuário (3 a 30 caracteres:
 * letras sem acento, números, ponto e sublinhado, começando e terminando com letra ou número).
 *
 * <p>Não reconhece "@" no meio de uma palavra (como em e-mails). Quem de fato é mencionado é decidido depois,
 * conferindo se o @usuário existe e participa do evento.
 */
public final class MentionParser {

    private static final Pattern MENTION = Pattern.compile(
            "(?<![\\p{L}\\p{N}_.@])@([A-Za-z0-9][A-Za-z0-9._]{1,28}[A-Za-z0-9])(?![A-Za-z0-9_])");

    private MentionParser() {
    }

    public static List<Candidate> find(String text) {
        List<Candidate> found = new ArrayList<>();
        if (text == null) {
            return found;
        }
        Matcher matcher = MENTION.matcher(text);
        while (matcher.find()) {
            String username = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!username.contains("..")) {
                found.add(new Candidate(username, matcher.start(), matcher.end() - matcher.start()));
            }
        }
        return found;
    }

    /**
     * @param username normalizado (minúsculas, sem "@")
     * @param start    posição do "@" (unidades UTF-16)
     * @param length   tamanho do trecho, incluindo o "@"
     */
    public record Candidate(String username, int start, int length) {
    }
}
