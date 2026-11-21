package br.com.memora.shared.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalização de texto para busca: sem acentos, em minúsculas e com espaços simples.
 * "Chá de  Fraldas" e "cha de fraldas" viram o mesmo termo.
 */
public final class SearchText {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private SearchText() {
    }

    /** Texto normalizado, ou {@code null} se vazio. */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        String normalized = SPACES.matcher(MARKS.matcher(decomposed).replaceAll("")).replaceAll(" ")
                .strip()
                .toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    /** Padrão LIKE "contém" para o termo normalizado, com os curingas do usuário escapados ({@code \}). */
    public static String containsPattern(String normalized) {
        String escaped = normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
