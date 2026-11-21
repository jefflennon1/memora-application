package br.com.memora.shared.storage;

import br.com.memora.shared.error.DomainException;
import org.springframework.http.HttpStatus;

/** O armazenamento de arquivos não respondeu ou não está configurado (HTTP 503). */
public class StorageUnavailableException extends DomainException {

    public StorageUnavailableException(Throwable cause) {
        super("STORAGE_UNAVAILABLE", "O armazenamento de arquivos está indisponível no momento. Tente novamente.");
        initCause(cause);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }
}
