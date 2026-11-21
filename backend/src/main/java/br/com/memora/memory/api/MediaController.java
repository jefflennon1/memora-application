package br.com.memora.memory.api;

import br.com.memora.identity.security.MemoraPrincipal;
import br.com.memora.identity.web.AllowIncompleteOnboarding;
import br.com.memora.memory.api.MemoryController.MediaResponse;
import br.com.memora.memory.application.MediaService;
import br.com.memora.memory.application.MediaService.MediaContent;
import br.com.memora.shared.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Mídia", description = "Envio de fotos, áudios e vídeos e acesso controlado aos arquivos.")
public class MediaController {

    private final MediaService media;

    public MediaController(MediaService media) {
        this.media = media;
    }

    @PostMapping(path = "/events/{eventId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    @Operation(summary = "Envia uma foto, áudio ou vídeo",
            description = "Participante, com o mural aberto. Campo `file` (multipart). Fotos JPEG, PNG, WebP ou GIF; "
                    + "áudios MP3, M4A, Ogg, FLAC ou WAV, com no máximo 30 segundos; vídeos MP4/MOV, com no máximo "
                    + "60 segundos e somente para memórias. O tipo e a duração são "
                    + "validados pelo conteúdo. A mídia fica pendente até ser usada e é apagada após 24 h se órfã.")
    public ResponseEntity<MediaResponse> upload(@AuthenticationPrincipal MemoraPrincipal principal,
                                                @PathVariable UUID eventId,
                                                @RequestPart("file") MultipartFile file) throws IOException {
        var uploaded = media.upload(eventId, principal.userId(), file.getBytes());
        return ResponseEntity.created(URI.create(uploaded.url())).body(MediaResponse.from(uploaded));
    }

    @GetMapping("/media/{id}")
    @AllowIncompleteOnboarding
    @Operation(summary = "Baixa um arquivo",
            description = "Entrega o arquivo só para quem pode ver a memória onde ele está (ou para quem o enviou, "
                    + "enquanto não foi publicado). Caso contrário, 404. Use em `<img src>` com cookies "
                    + "(o frontend e a API são o mesmo site).")
    public ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal MemoraPrincipal principal,
                                                        @PathVariable UUID id,
                                                        @RequestHeader(value = HttpHeaders.RANGE, required = false)
                                                        String range,
                                                        WebRequest request) throws IOException {
        MediaContent content = media.open(id, principal == null ? null : principal.userId(), range);
        // O conteúdo de um id nunca muda: o próprio id serve de ETag.
        String etag = "\"" + content.id() + "\"";
        CacheControl cache = CacheControl.maxAge(Duration.ofHours(1)).cachePrivate();
        if (request.checkNotModified(etag)) {
            content.object().content().close();
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cache).build();
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.status(
                        content.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .eTag(etag)
                .cacheControl(cache)
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.object().length())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header("X-Content-Type-Options", "nosniff")
                .header("Cross-Origin-Resource-Policy", "same-site");
        if (content.partial()) {
            response.header(HttpHeaders.CONTENT_RANGE, "bytes " + content.start() + "-" + content.end() + "/"
                    + content.totalLength());
        }
        return response.body(new InputStreamResource(content.object().content()));
    }

    @DeleteMapping("/media/{id}")
    @SecurityRequirement(name = OpenApiConfig.SESSION_SCHEME)
    @Operation(summary = "Descarta uma mídia não publicada",
            description = "Só quem enviou, enquanto ela não faz parte de uma memória.")
    public ResponseEntity<Void> discard(@AuthenticationPrincipal MemoraPrincipal principal, @PathVariable UUID id) {
        media.discardPending(id, principal.userId());
        return ResponseEntity.noContent().build();
    }
}
