package br.com.memora.shared.error;

import org.springframework.http.HttpStatus;

/** Arquivo ou requisição acima do tamanho permitido (HTTP 413). */
public class PayloadTooLargeException extends DomainException {

    public PayloadTooLargeException(String code, String message) {
        super(code, message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.PAYLOAD_TOO_LARGE;
    }
}
