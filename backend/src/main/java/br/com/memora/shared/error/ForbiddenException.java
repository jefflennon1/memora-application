package br.com.memora.shared.error;

import org.springframework.http.HttpStatus;

/** O usuário está autenticado, mas não possui permissão para a operação (HTTP 403). */
public class ForbiddenException extends DomainException {

    public ForbiddenException(String code, String message) {
        super(code, message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.FORBIDDEN;
    }
}
