package br.com.memora.shared.error;

import org.springframework.http.HttpStatus;

/** Tipo de arquivo não aceito (HTTP 415). */
public class UnsupportedMediaException extends DomainException {

    public UnsupportedMediaException(String code, String message) {
        super(code, message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNSUPPORTED_MEDIA_TYPE;
    }
}
