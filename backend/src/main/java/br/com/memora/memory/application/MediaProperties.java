package br.com.memora.memory.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites de mídia ({@code memora.media.*}).
 *
 * @param maxImageBytes   tamanho máximo de cada imagem
 * @param maxAudioBytes   tamanho máximo de cada áudio (sempre limitado também a 30 segundos)
 * @param maxVideoBytes   tamanho máximo de cada vídeo (sempre limitado também a 60 segundos)
 * @param orphanRetention por quanto tempo uma mídia enviada e não publicada é guardada
 */
@ConfigurationProperties(prefix = "memora.media")
public record MediaProperties(Long maxImageBytes, Long maxAudioBytes, Long maxVideoBytes, Duration orphanRetention) {

    public static final int MAX_PENDING_PER_EVENT = 30;

    public MediaProperties {
        maxImageBytes = maxImageBytes == null ? 15L * 1024 * 1024 : maxImageBytes;
        maxAudioBytes = maxAudioBytes == null ? 10L * 1024 * 1024 : maxAudioBytes;
        maxVideoBytes = maxVideoBytes == null ? 100L * 1024 * 1024 : maxVideoBytes;
        orphanRetention = orphanRetention == null ? Duration.ofHours(24) : orphanRetention;
    }
}
