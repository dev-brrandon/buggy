package de.legitserver.assistant.observability.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Compatibility default for adapters that have not yet been wired to the runtime registry. */
public enum NoOpAssistantMetrics implements AssistantMetrics {
    INSTANCE;

    @Override
    public void increment(AssistantMetric metric) {}

    @Override
    public void add(AssistantMetric metric, long delta) {}

    @Override
    public void setGauge(AssistantMetric metric, long value) {}

    @Override
    public void recordLatency(AssistantMetric metric, Duration duration) {}

    @Override
    public MetricsSnapshot snapshot() {
        return new MetricsSnapshot(Instant.EPOCH, Map.of(), Map.of());
    }
}
