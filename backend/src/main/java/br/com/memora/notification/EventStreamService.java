package br.com.memora.notification;

import br.com.memora.event.application.EventAccess;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Conexoes SSE do mural. Esta primeira versao mantem as conexoes na instancia atual da API. */
@Service
public class EventStreamService {

    private static final long RETRY_MILLIS = 5_000;

    private final EventAccess events;
    private final Map<UUID, Map<UUID, Subscriber>> subscribers = new ConcurrentHashMap<>();

    public EventStreamService(EventAccess events) {
        this.events = events;
    }

    public SseEmitter subscribe(UUID eventId, UUID userId) {
        events.requireVisible(eventId, userId).requireParticipant();

        SseEmitter emitter = new SseEmitter(0L);
        UUID subscriptionId = UUID.randomUUID();
        Subscriber subscriber = new Subscriber(userId, emitter);
        subscribers.computeIfAbsent(eventId, ignored -> new ConcurrentHashMap<>())
                .put(subscriptionId, subscriber);

        Runnable remove = () -> remove(eventId, subscriptionId);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ignored -> remove.run());

        try {
            emitter.send(SseEmitter.event().name("ready").reconnectTime(RETRY_MILLIS)
                    .data(Map.of("eventId", eventId)));
        } catch (IOException | IllegalStateException exception) {
            remove.run();
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    /** So avisa depois do commit; uma publicacao revertida nunca aparece no stream. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void memoryCreated(MemoryCreated event) {
        send(event.eventId(), "memory-created",
                new MemoryCreatedNotice(event.eventId(), event.memoryId(), event.createdAt()), event.authorId());
    }

    /** Mantem conexoes vivas atraves de proxies que encerram respostas ociosas. */
    @Scheduled(fixedRate = 20_000)
    void heartbeat() {
        subscribers.keySet().forEach(eventId -> send(eventId, "heartbeat", Map.of("at", Instant.now()), null));
    }

    private void send(UUID eventId, String eventName, Object data, UUID excludedUserId) {
        Map<UUID, Subscriber> eventSubscribers = subscribers.get(eventId);
        if (eventSubscribers == null) {
            return;
        }
        eventSubscribers.forEach((subscriptionId, subscriber) -> {
            if (excludedUserId != null && excludedUserId.equals(subscriber.userId())) {
                return;
            }
            try {
                subscriber.emitter().send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException exception) {
                subscriber.emitter().complete();
                remove(eventId, subscriptionId);
            }
        });
    }

    private void remove(UUID eventId, UUID subscriptionId) {
        subscribers.computeIfPresent(eventId, (ignored, current) -> {
            current.remove(subscriptionId);
            return current.isEmpty() ? null : current;
        });
    }

    private record Subscriber(UUID userId, SseEmitter emitter) {
    }

    public record MemoryCreated(UUID eventId, UUID memoryId, UUID authorId, Instant createdAt) {
    }

    public record MemoryCreatedNotice(UUID eventId, UUID memoryId, Instant createdAt) {
    }
}
