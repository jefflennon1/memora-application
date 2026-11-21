package br.com.memora.shared.error;

import org.springframework.http.HttpStatus;

/** Dado de entrada inválido segundo uma regra de domínio (ex.: formato do @usuário) (HTTP 400). */
public class ValidationException extends DomainException {

    public ValidationException(String code, String message) {
        super(code, message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.BAD_REQUEST;
    }
}
