package br.com.memora.shared.storage;

/** O objeto não existe no armazenamento (ex.: removido manualmente). */
public class StoredObjectNotFoundException extends RuntimeException {

    public StoredObjectNotFoundException(String key) {
        super("Objeto inexistente: " + key);
    }
}
