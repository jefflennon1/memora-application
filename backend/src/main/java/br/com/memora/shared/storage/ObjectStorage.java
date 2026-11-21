package br.com.memora.shared.storage;

import java.io.InputStream;

/**
 * Armazenamento de arquivos (objetos) fora do banco. Implementação padrão: S3/MinIO ({@link S3ObjectStorage}).
 * Nenhum objeto é público: quem decide quem pode ler é sempre a aplicação.
 */
public interface ObjectStorage {

    void put(String key, byte[] content, String contentType);

    /** Abre o objeto para leitura. O chamador fecha o stream. */
    StoredObject get(String key);

    /** Abre um intervalo inclusivo do objeto, para reprodução com HTTP Range. */
    StoredObject get(String key, long start, long endInclusive);

    long length(String key);

    /** Remove o objeto. Remover algo inexistente não é erro. */
    void delete(String key);

    record StoredObject(InputStream content, long length) {
    }
}
