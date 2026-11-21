package br.com.memora.shared.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StorageConfig {

    @Bean(destroyMethod = "close")
    S3ObjectStorage objectStorage(StorageProperties properties) {
        return new S3ObjectStorage(properties);
    }
}
