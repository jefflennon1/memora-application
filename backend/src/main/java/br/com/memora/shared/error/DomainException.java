package br.com.memora.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Exceção base das regras de negócio. Cada subclasse define o status HTTP e um código estável,
 * convertidos em Problem Details (RFC 9457) por {@link ApiExceptionHandler}.
 */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** Código estável e legível por máquina (ex.: {@code EVENT_START_IN_PAST}). */
    public String code() {
        return code;
    }

    public abstract HttpStatus status();
}
