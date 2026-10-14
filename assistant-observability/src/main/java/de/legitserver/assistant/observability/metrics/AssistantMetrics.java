package de.legitserver.assistant.observability.metrics;

import java.time.Duration;

/** Write/read boundary for the bounded in-process metric foundation. */
public interface AssistantMetrics {
    void increment(AssistantMetric metric);

    void add(AssistantMetric metric, long delta);

    void setGauge(AssistantMetric metric, long value);

    void recordLatency(AssistantMetric metric, Duration duration);

    MetricsSnapshot snapshot();
}
