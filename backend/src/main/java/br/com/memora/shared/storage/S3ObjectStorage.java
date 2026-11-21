package br.com.memora.shared.storage;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * {@link ObjectStorage} sobre S3 ou qualquer serviço compatível (MinIO). Com {@code endpoint} configurado usa
 * endereçamento por caminho ({@code http://host/bucket/key}), exigido pelo MinIO.
 */
public class S3ObjectStorage implements ObjectStorage, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);

    private final S3Client client;
    private final String bucket;

    public S3ObjectStorage(StorageProperties properties) {
        S3ClientBuilder builder = S3Client.builder().region(Region.of(properties.region()));
        if (StringUtils.hasText(properties.endpoint())) {
            builder.endpointOverride(URI.create(properties.endpoint())).forcePathStyle(true);
        }
        if (properties.hasCredentials()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())));
        } else {
            log.warn("Armazenamento sem STORAGE_ACCESS_KEY/STORAGE_SECRET_KEY: usando as credenciais padrão do "
                    + "ambiente. Em desenvolvimento, configure o .env para o MinIO local.");
            builder.credentialsProvider(DefaultCredentialsProvider.builder().build());
        }
        this.client = builder.build();
        this.bucket = properties.bucket();
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        try {
            client.putObject(b -> b.bucket(bucket).key(key).contentType(contentType)
                    .contentLength((long) content.length), RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw new StorageUnavailableException(e);
        }
    }

    @Override
    public StoredObject get(String key) {
        try {
            ResponseInputStream<GetObjectResponse> stream = client.getObject(b -> b.bucket(bucket).key(key));
            return new StoredObject(stream, stream.response().contentLength());
        } catch (NoSuchKeyException e) {
            throw new StoredObjectNotFoundException(key);
        } catch (SdkException e) {
            throw new StorageUnavailableException(e);
        }
    }

    @Override
    public StoredObject get(String key, long start, long endInclusive) {
        try {
            ResponseInputStream<GetObjectResponse> stream = client.getObject(b -> b.bucket(bucket).key(key)
                    .range("bytes=" + start + "-" + endInclusive));
            return new StoredObject(stream, stream.response().contentLength());
        } catch (NoSuchKeyException e) {
            throw new StoredObjectNotFoundException(key);
        } catch (SdkException e) {
            throw new StorageUnavailableException(e);
        }
    }

    @Override
    public long length(String key) {
        try {
            return client.headObject(b -> b.bucket(bucket).key(key)).contentLength();
        } catch (NoSuchKeyException e) {
            throw new StoredObjectNotFoundException(key);
        } catch (SdkException e) {
            throw new StorageUnavailableException(e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(b -> b.bucket(bucket).key(key));
        } catch (SdkException e) {
            throw new StorageUnavailableException(e);
        }
    }

    @Override
    public void close() {
        client.close();
    }
}
