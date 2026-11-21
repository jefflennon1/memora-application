package br.com.memora.identity.application;

import br.com.memora.shared.error.DomainException;
import org.springframework.http.HttpStatus;

/** Usuário ou senha incorretos; a resposta não informa qual dos dois falhou. */
public class InvalidCredentialsException extends DomainException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Nome de usuário ou senha incorretos.");
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.UNAUTHORIZED;
    }
}
