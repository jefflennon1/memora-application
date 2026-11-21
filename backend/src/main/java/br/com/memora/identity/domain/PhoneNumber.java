package br.com.memora.identity.domain;

import br.com.memora.shared.error.ValidationException;

/** Normalização mínima para telefone nacional ou internacional: somente 10 a 15 dígitos. */
public final class PhoneNumber {

    private PhoneNumber() {
    }

    public static String parse(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (digits.length() < 10 || digits.length() > 15) {
            throw new ValidationException("INVALID_PHONE",
                    "Informe um telefone válido, com DDD e entre 10 e 15 dígitos.");
        }
        return digits;
    }
}
