package de.legitserver.assistant.supervisor.health;

import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.observability.metrics.LatencySnapshot;
import java.time.Instant;

/** Bounded per-assistant operational metrics used by the Supervisor read model. */
public record AssistantMetricsSnapshot(
        AssistantId assistantId,
        Instant capturedAt,
        long requests,
        long successfulRequests,
        long errors,
        double successRate,
        double errorRate,
        LatencySnapshot latency,
        long toolCalls,
        long toolFailures,
        long timeouts,
        long rateLimitHits,
        long authorizationDenials,
        long activeConversations,
        long queueDepth) {
    public AssistantMetricsSnapshot {
        if (assistantId == null || capturedAt == null || latency == null) {
            throw new IllegalArgumentException("metrics snapshot fields must not be null");
        }
        if (requests < 0 || successfulRequests < 0 || errors < 0 || toolCalls < 0 || toolFailures < 0
                || timeouts < 0 || rateLimitHits < 0 || authorizationDenials < 0
                || activeConversations < 0 || queueDepth < 0) {
            throw new IllegalArgumentException("metrics must not be negative");
        }
        if (!validRate(successRate) || !validRate(errorRate)) {
            throw new IllegalArgumentException("rates must be between 0 and 1");
        }
    }

    private static boolean validRate(double value) {
        return !Double.isNaN(value) && value >= 0.0 && value <= 1.0;
    }
}
