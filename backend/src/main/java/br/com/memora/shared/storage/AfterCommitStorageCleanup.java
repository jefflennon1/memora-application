package br.com.memora.shared.storage;

import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Apaga objetos do armazenamento só depois que a transação do banco é confirmada. Se a transação falhar, nada é
 * apagado; se a remoção falhar, o erro é registrado e a operação do usuário não é afetada.
 */
@Component
public class AfterCommitStorageCleanup {

    private static final Logger log = LoggerFactory.getLogger(AfterCommitStorageCleanup.class);

    private final ObjectStorage storage;

    public AfterCommitStorageCleanup(ObjectStorage storage) {
        this.storage = storage;
    }

    public void deleteAfterCommit(Collection<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        List<String> copy = List.copyOf(keys);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteNow(copy);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteNow(copy);
            }
        });
    }

    private void deleteNow(List<String> keys) {
        for (String key : keys) {
            try {
                storage.delete(key);
            } catch (RuntimeException e) {
                log.warn("Não foi possível apagar o objeto {} do armazenamento", key, e);
            }
        }
    }
}
