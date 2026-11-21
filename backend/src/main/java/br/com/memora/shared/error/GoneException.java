package br.com.memora.shared.error;

import org.springframework.http.HttpStatus;

/** O recurso existiu, mas não pode mais ser usado (ex.: convite expirado ou revogado) (HTTP 410). */
public class GoneException extends DomainException {

    public GoneException(String code, String message) {
        super(code, message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.GONE;
    }
}
