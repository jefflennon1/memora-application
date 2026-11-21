package br.com.memora.shared.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** Configuração do armazenamento de objetos ({@code memora.storage.*}). */
@ConfigurationProperties(prefix = "memora.storage")
public record StorageProperties(String endpoint, String region, String bucket, String accessKey, String secretKey) {

    public StorageProperties {
        region = StringUtils.hasText(region) ? region : "us-east-1";
        bucket = StringUtils.hasText(bucket) ? bucket : "memora-media";
    }

    public boolean hasCredentials() {
        return StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey);
    }
}
