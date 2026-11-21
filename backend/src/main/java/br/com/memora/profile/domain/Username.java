package br.com.memora.profile.domain;

import br.com.memora.shared.error.ValidationException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regras do @usuário.
 *
 * <ul>
 *   <li>3 a 30 caracteres: letras minúsculas sem acento, números, ponto e sublinhado.</li>
 *   <li>Começa e termina com letra ou número; sem pontos consecutivos.</li>
 *   <li>Não diferencia maiúsculas de minúsculas: é armazenado em minúsculas.</li>
 *   <li>O "@" inicial é opcional na entrada e não faz parte do valor armazenado.</li>
 *   <li>Alguns nomes são reservados para a plataforma.</li>
 * </ul>
 */
public final class Username {

    public static final String RULES =
            "Use de 3 a 30 caracteres: letras sem acento, números, ponto (.) ou sublinhado (_), "
                    + "começando e terminando com letra ou número.";

    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9][a-z0-9._]{1,28}[a-z0-9]$");

    private static final Set<String> RESERVED = Set.of(
            "admin", "administrador", "administrator", "root", "system", "sistema",
            "memora", "api", "app", "www", "help", "ajuda", "support", "suporte",
            "moderador", "moderator", "moderacao", "staff", "oficial", "official",
            "me", "eu", "user", "users", "usuario", "usuarios", "null", "undefined",
            "login", "logout", "oauth2", "auth", "settings", "configuracoes",
            "events", "eventos", "invites", "convites", "memories", "memorias");

    private Username() {
    }

    /** Normaliza a entrada (remove "@" inicial, espaços nas pontas e converte para minúsculas). */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.strip();
        if (value.startsWith("@")) {
            value = value.substring(1);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    /** Motivo pelo qual o valor normalizado é inválido, se houver. */
    public static Optional<String> problemWith(String normalized) {
        if (!FORMAT.matcher(normalized).matches() || normalized.contains("..")) {
            return Optional.of("INVALID_USERNAME");
        }
        if (RESERVED.contains(normalized)) {
            return Optional.of("USERNAME_RESERVED");
        }
        return Optional.empty();
    }

    /** Normaliza e valida; lança {@link ValidationException} se inválido. */
    public static String parse(String raw) {
        String normalized = normalize(raw);
        Optional<String> problem = problemWith(normalized);
        if (problem.isPresent()) {
            String message = problem.get().equals("USERNAME_RESERVED")
                    ? "Este @usuário é reservado pela Memora. Escolha outro."
                    : "@usuário inválido. " + RULES;
            throw new ValidationException(problem.get(), message);
        }
        return normalized;
    }
}
