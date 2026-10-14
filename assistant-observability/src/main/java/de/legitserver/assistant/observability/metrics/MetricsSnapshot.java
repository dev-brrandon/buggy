package de.legitserver.assistant.observability.metrics;

import java.time.Instant;
import java.util.Map;

/** Immutable point-in-time view of the complete Phase-6 metric catalog. */
public record MetricsSnapshot(
        Instant capturedAt,
        Map<AssistantMetric, Long> scalarValues,
        Map<AssistantMetric, LatencySnapshot> latencyValues) {
    public MetricsSnapshot {
        if (capturedAt == null) throw new IllegalArgumentException("capturedAt must not be null");
        scalarValues = scalarValues == null ? Map.of() : Map.copyOf(scalarValues);
        latencyValues = latencyValues == null ? Map.of() : Map.copyOf(latencyValues);
    }

    public long scalar(AssistantMetric metric) {
        return scalarValues.getOrDefault(metric, 0L);
    }

    public LatencySnapshot latency(AssistantMetric metric) {
        return latencyValues.getOrDefault(metric, LatencySnapshot.empty());
    }
}
