package br.com.memora.shared.error;

import org.springframework.http.HttpStatus;

/** Conflito com o estado atual (ex.: @usuário já utilizado) (HTTP 409). */
public class ConflictException extends DomainException {

    public ConflictException(String code, String message) {
        super(code, message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
