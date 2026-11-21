package br.com.memora.identity.application;

import br.com.memora.shared.error.ForbiddenException;

/** A conta existe, mas está suspensa ou excluída. */
public class AccountDisabledException extends ForbiddenException {

    public AccountDisabledException() {
        super("ACCOUNT_DISABLED", "Esta conta está desativada.");
    }
}
