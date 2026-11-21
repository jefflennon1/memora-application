package br.com.memora.support;

import br.com.memora.shared.storage.ObjectStorage;
import br.com.memora.shared.storage.StoredObjectNotFoundException;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Armazenamento em memória para os testes de integração da aplicação. A implementação S3 real é testada à parte,
 * contra um MinIO em container ({@code S3ObjectStorageIT}).
 */
public class InMemoryObjectStorage implements ObjectStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public void put(String key, byte[] content, String contentType) {
        objects.put(key, content.clone());
    }

    @Override
    public StoredObject get(String key) {
        byte[] content = objects.get(key);
        if (content == null) {
            throw new StoredObjectNotFoundException(key);
        }
        return new StoredObject(new ByteArrayInputStream(content), content.length);
    }

    @Override
    public StoredObject get(String key, long start, long endInclusive) {
        byte[] content = objects.get(key);
        if (content == null) throw new StoredObjectNotFoundException(key);
        byte[] part = Arrays.copyOfRange(content, Math.toIntExact(start), Math.toIntExact(endInclusive + 1));
        return new StoredObject(new ByteArrayInputStream(part), part.length);
    }

    @Override
    public long length(String key) {
        byte[] content = objects.get(key);
        if (content == null) throw new StoredObjectNotFoundException(key);
        return content.length;
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }

    public boolean contains(String key) {
        return objects.containsKey(key);
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {

        @Bean
        @Primary
        InMemoryObjectStorage inMemoryObjectStorage() {
            return new InMemoryObjectStorage();
        }
    }
}
