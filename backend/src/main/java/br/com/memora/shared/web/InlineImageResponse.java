package br.com.memora.shared.web;

import br.com.memora.shared.storage.StoredImage;
import java.io.IOException;
import java.time.Duration;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

/** Cabeçalhos seguros e cache condicional para fotos entregues pela própria API. */
public final class InlineImageResponse {

    private InlineImageResponse() {
    }

    public static ResponseEntity<InputStreamResource> of(StoredImage image, WebRequest request) throws IOException {
        String etag = "\"" + image.storageKey().substring(image.storageKey().lastIndexOf('/') + 1) + "\"";
        CacheControl cache = CacheControl.maxAge(Duration.ofHours(1)).cachePrivate();
        if (request.checkNotModified(etag)) {
            image.object().content().close();
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cache).build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(cache)
                .contentType(MediaType.parseMediaType(image.contentType()))
                .contentLength(image.object().length())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .header("Cross-Origin-Resource-Policy", "same-site")
                .body(new InputStreamResource(image.object().content()));
    }
}
