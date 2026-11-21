package br.com.memora.memory.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Apaga, periodicamente, imagens enviadas e nunca publicadas (ex.: a pessoa desistiu da memória).
 * Desligável com {@code memora.media.cleanup-enabled=false}.
 */
@Component
@ConditionalOnProperty(prefix = "memora.media", name = "cleanup-enabled", havingValue = "true", matchIfMissing = true)
class OrphanMediaCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OrphanMediaCleanupJob.class);
    private static final int MAX_BATCHES_PER_RUN = 50;

    private final MediaService media;

    OrphanMediaCleanupJob(MediaService media) {
        this.media = media;
    }

    @Scheduled(initialDelayString = "${memora.media.cleanup-initial-delay:PT5M}",
            fixedDelayString = "${memora.media.cleanup-interval:PT1H}")
    void purge() {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            int removed = media.purgeOrphans();
            total += removed;
            if (removed == 0) {
                break;
            }
        }
        if (total > 0) {
            log.info("{} imagem(ns) não publicada(s) apagada(s)", total);
        }
    }
}
