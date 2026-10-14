package de.legitserver.assistant.bootstrap.observability;

import de.legitserver.assistant.observability.health.AssistantHealthService;
import de.legitserver.assistant.observability.metrics.AssistantMetric;
import de.legitserver.assistant.observability.metrics.AssistantMetrics;
import de.legitserver.assistant.observability.metrics.LatencySnapshot;
import de.legitserver.assistant.observability.metrics.MetricKind;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/** Exports the bounded Assistant metric registry onto Spring Boot's local Micrometer surface. */
public final class Phase6MetricsBinder implements MeterBinder {
    private final AssistantMetrics metrics;
    private final AssistantHealthService health;

    public Phase6MetricsBinder(AssistantMetrics metrics, AssistantHealthService health) {
        if (metrics == null || health == null) throw new IllegalArgumentException("metrics binder dependency must not be null");
        this.metrics = metrics;
        this.health = health;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        if (registry == null) throw new IllegalArgumentException("registry must not be null");
        for (AssistantMetric metric : AssistantMetric.values()) {
            if (metric == AssistantMetric.ASSISTANT_HEALTH) continue;
            if (metric.kind() == MetricKind.COUNTER) {
                FunctionCounter.builder(metric.wireName(), metrics, value -> value.snapshot().scalar(metric))
                        .description("Legit Assistant Phase-6 counter")
                        .register(registry);
            } else if (metric.kind() == MetricKind.GAUGE) {
                Gauge.builder(metric.wireName(), metrics, value -> value.snapshot().scalar(metric))
                        .description("Legit Assistant Phase-6 gauge")
                        .register(registry);
            } else {
                bindLatency(registry, metric);
            }
        }
        Gauge.builder(
                        AssistantMetric.ASSISTANT_HEALTH.wireName(),
                        health,
                        value -> value.snapshot().ready() ? 1.0 : 0.0)
                .description("1 when required Assistant dependencies are ready, otherwise 0")
                .register(registry);
    }

    private void bindLatency(MeterRegistry registry, AssistantMetric metric) {
        Gauge.builder(metric.wireName() + ".average.ms", metrics, value -> latency(value, metric).averageMs())
                .register(registry);
        Gauge.builder(metric.wireName() + ".p50.ms", metrics, value -> latency(value, metric).p50Ms())
                .register(registry);
        Gauge.builder(metric.wireName() + ".p95.ms", metrics, value -> latency(value, metric).p95Ms())
                .register(registry);
        Gauge.builder(metric.wireName() + ".p99.ms", metrics, value -> latency(value, metric).p99Ms())
                .register(registry);
    }

    private static LatencySnapshot latency(AssistantMetrics metrics, AssistantMetric metric) {
        return metrics.snapshot().latency(metric);
    }
}
