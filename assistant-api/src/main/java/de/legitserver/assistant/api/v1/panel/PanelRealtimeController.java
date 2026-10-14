package de.legitserver.assistant.api.v1.panel;

import de.legitserver.assistant.api.v1.AssistantApiV1;
import de.legitserver.assistant.panel.realtime.PanelRealtimeEvent;
import de.legitserver.assistant.panel.realtime.PanelRealtimeHub;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Bounded SSE stream for sanitized Panel notifications; full data remains retrievable through normal API reads. */
@RestController
@RequestMapping(AssistantApiV1.BASE_PATH + "/panel")
public final class PanelRealtimeController {
    private static final long SSE_TIMEOUT_MS = 60_000L;
    private final PanelRealtimeHub hub;

    public PanelRealtimeController(PanelRealtimeHub hub) {
        this.hub = hub;
    }

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        long after = parseLastEventId(lastEventId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicReference<AutoCloseable> subscriptionRef = new AtomicReference<>();
        try {
            List<PanelRealtimeEvent> replay = hub.replayAfter(after, 100);
            for (PanelRealtimeEvent event : replay) send(emitter, event);
            try {
                emitter.send(SseEmitter.event().comment("connected").reconnectTime(5_000L));
            } catch (IOException ioFailure) {
                throw new IllegalStateException("SSE_CONNECT_FAILED", ioFailure);
            }
            AutoCloseable subscription = hub.subscribe(event -> {
                try {
                    send(emitter, event);
                } catch (RuntimeException sendFailure) {
                    closeQuietly(subscriptionRef.getAndSet(null));
                    emitter.completeWithError(sendFailure);
                }
            });
            subscriptionRef.set(subscription);
            emitter.onCompletion(() -> closeQuietly(subscriptionRef.getAndSet(null)));
            emitter.onTimeout(() -> closeQuietly(subscriptionRef.getAndSet(null)));
            emitter.onError(error -> closeQuietly(subscriptionRef.getAndSet(null)));
            return emitter;
        } catch (RuntimeException failure) {
            closeQuietly(subscriptionRef.getAndSet(null));
            throw failure;
        }
    }

    private static long parseLastEventId(String raw) {
        if (raw == null || raw.isBlank()) return 0L;
        try {
            long value = Long.parseLong(raw);
            if (value < 0) throw new IllegalArgumentException("Last-Event-ID must be >= 0");
            return value;
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException("Last-Event-ID invalid", malformed);
        }
    }

    private static void send(SseEmitter emitter, PanelRealtimeEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(Long.toString(event.sequence()))
                    .name(event.type().name())
                    .data(event));
        } catch (IOException ioFailure) {
            throw new IllegalStateException("SSE_SEND_FAILED", ioFailure);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
            // No user-visible action; subscription release is best-effort during disconnect.
        }
    }
}
