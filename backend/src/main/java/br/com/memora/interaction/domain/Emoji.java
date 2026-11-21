package br.com.memora.interaction.domain;

import br.com.memora.shared.error.ValidationException;
import java.util.regex.Pattern;

/**
 * Validação e normalização do emoji de uma reação.
 *
 * <p>Aceita exatamente um emoji, incluindo sequências que aparecem como um só desenho: tons de pele (👍🏽),
 * combinações com ZWJ (👨‍👩‍👧), bandeiras (🇧🇷, 🏴󠁧󠁢󠁳󠁣󠁴󠁿) e teclas (1️⃣). Recusa texto, números soltos e mais de
 * um emoji.
 *
 * <p>Normaliza o seletor de variação ({@code U+FE0F}) de emojis de um só caractere, para que "❤" e "❤️" contem
 * como o mesmo emoji no resumo.
 */
public final class Emoji {

    /** Limite de code points de uma sequência (as famílias com tons de pele chegam a ~10). */
    static final int MAX_CODE_POINTS = 16;

    private static final Pattern SINGLE_GRAPHEME = Pattern.compile("\\X");
    private static final int VS15 = 0xFE0E;
    private static final int VS16 = 0xFE0F;
    private static final int ZWJ = 0x200D;
    private static final int KEYCAP = 0x20E3;

    private Emoji() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            throw invalid();
        }
        String value = raw.strip();
        if (value.isEmpty() || value.codePointCount(0, value.length()) > MAX_CODE_POINTS
                || !SINGLE_GRAPHEME.matcher(value).matches()) {
            throw invalid();
        }
        int[] cps = value.codePoints().toArray();
        if (!isEmojiSequence(cps)) {
            throw invalid();
        }
        return canonical(cps, value);
    }

    private static boolean isEmojiSequence(int[] cps) {
        boolean pictographic = false;
        boolean keycap = false;
        int regionalIndicators = 0;
        for (int cp : cps) {
            if (Character.isExtendedPictographic(cp)) {
                pictographic = true;
            } else if (cp >= 0x1F1E6 && cp <= 0x1F1FF) {
                regionalIndicators++;
            } else if (cp == KEYCAP) {
                keycap = true;
            } else if (!(Character.isEmoji(cp) || Character.isEmojiComponent(cp) || cp == ZWJ || cp == VS16
                    || cp == VS15 || (cp >= 0xE0020 && cp <= 0xE007F))) {
                return false;
            }
        }
        if (keycap) {
            return cps.length >= 2 && isKeycapBase(cps[0]);
        }
        return pictographic || (regionalIndicators == 2 && cps.length == 2);
    }

    private static boolean isKeycapBase(int cp) {
        return (cp >= '0' && cp <= '9') || cp == '#' || cp == '*';
    }

    /** Emoji de um só caractere: com {@code U+FE0F} quando o padrão dele é aparecer como texto, sem quando não. */
    private static String canonical(int[] cps, String value) {
        boolean single = cps.length == 1 || (cps.length == 2 && (cps[1] == VS16 || cps[1] == VS15));
        if (!single) {
            return value;
        }
        int base = cps[0];
        return Character.isEmojiPresentation(base)
                ? new String(Character.toChars(base))
                : new String(Character.toChars(base)) + (char) VS16;
    }

    private static ValidationException invalid() {
        return new ValidationException("INVALID_EMOJI", "Escolha um emoji para reagir.");
    }
}
