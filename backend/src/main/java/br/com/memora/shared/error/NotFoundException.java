package br.com.memora.shared.error;

import org.springframework.http.HttpStatus;

/** Recurso inexistente ou não visível ao usuário atual (HTTP 404). Também usado para não revelar a existência de recursos privados. */
public class NotFoundException extends DomainException {

    public NotFoundException(String code, String message) {
        super(code, message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }
}
