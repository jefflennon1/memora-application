package br.com.memora.shared.error;

import org.springframework.http.HttpStatus;

/** Violação de regra de negócio (ex.: mural fechado, data no passado) (HTTP 422). */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(String code, String message) {
        super(code, message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
