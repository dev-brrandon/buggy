package de.legitserver.assistant.observability.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** Thread-safe bounded metric registry with a fixed catalog and bounded latency reservoirs. */
public final class InMemoryAssistantMetrics implements AssistantMetrics {
    private static final int LATENCY_WINDOW = 1024;

    private final EnumMap<AssistantMetric, LongAdder> counters = new EnumMap<>(AssistantMetric.class);
    private final EnumMap<AssistantMetric, AtomicLong> gauges = new EnumMap<>(AssistantMetric.class);
    private final EnumMap<AssistantMetric, BoundedLatency> latencies = new EnumMap<>(AssistantMetric.class);

    public InMemoryAssistantMetrics() {
        for (AssistantMetric metric : AssistantMetric.values()) {
            switch (metric.kind()) {
                case COUNTER -> counters.put(metric, new LongAdder());
                case GAUGE -> gauges.put(metric, new AtomicLong());
                case LATENCY -> latencies.put(metric, new BoundedLatency(LATENCY_WINDOW));
            }
        }
    }

    @Override
    public void increment(AssistantMetric metric) {
        add(metric, 1L);
    }

    @Override
    public void add(AssistantMetric metric, long delta) {
        requireKind(metric, MetricKind.COUNTER);
        if (delta < 0) throw new IllegalArgumentException("counter delta must not be negative");
        counters.get(metric).add(delta);
    }

    @Override
    public void setGauge(AssistantMetric metric, long value) {
        requireKind(metric, MetricKind.GAUGE);
        if (value < 0) throw new IllegalArgumentException("gauge value must not be negative");
        gauges.get(metric).set(value);
    }

    @Override
    public void recordLatency(AssistantMetric metric, Duration duration) {
        requireKind(metric, MetricKind.LATENCY);
        if (duration == null || duration.isNegative()) throw new IllegalArgumentException("duration must not be negative");
        latencies.get(metric).record(Math.max(0L, duration.toMillis()));
    }

    @Override
    public MetricsSnapshot snapshot() {
        EnumMap<AssistantMetric, Long> scalar = new EnumMap<>(AssistantMetric.class);
        counters.forEach((metric, value) -> scalar.put(metric, value.sum()));
        gauges.forEach((metric, value) -> scalar.put(metric, value.get()));
        EnumMap<AssistantMetric, LatencySnapshot> timing = new EnumMap<>(AssistantMetric.class);
        latencies.forEach((metric, value) -> timing.put(metric, value.snapshot()));
        return new MetricsSnapshot(Instant.now(), Map.copyOf(scalar), Map.copyOf(timing));
    }

    private static void requireKind(AssistantMetric metric, MetricKind expected) {
        if (metric == null) throw new IllegalArgumentException("metric must not be null");
        if (metric.kind() != expected) {
            throw new IllegalArgumentException("metric " + metric + " must be " + expected);
        }
    }

    private static final class BoundedLatency {
        private final long[] samples;
        private long totalCount;
        private double totalMs;
        private int size;
        private int cursor;

        private BoundedLatency(int capacity) {
            samples = new long[capacity];
        }

        private synchronized void record(long millis) {
            samples[cursor] = millis;
            cursor = (cursor + 1) % samples.length;
            if (size < samples.length) size++;
            totalCount++;
            totalMs += millis;
        }

        private synchronized LatencySnapshot snapshot() {
            if (size == 0) return LatencySnapshot.empty();
            long[] recent = Arrays.copyOf(samples, size);
            Arrays.sort(recent);
            return new LatencySnapshot(
                    totalCount,
                    size,
                    totalMs / totalCount,
                    recent[0],
                    recent[size - 1],
                    percentile(recent, 0.50),
                    percentile(recent, 0.95),
                    percentile(recent, 0.99));
        }

        private static long percentile(long[] sorted, double percentile) {
            int index = (int) Math.ceil(percentile * sorted.length) - 1;
            return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
        }
    }
}
