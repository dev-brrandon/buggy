package de.legitserver.assistant.observability.metrics;

/** Bounded rolling latency summary in milliseconds. */
public record LatencySnapshot(
        long totalCount,
        long recentCount,
        double averageMs,
        long minMs,
        long maxMs,
        long p50Ms,
        long p95Ms,
        long p99Ms) {

    public LatencySnapshot {
        if (totalCount < 0 || recentCount < 0 || averageMs < 0.0 || minMs < 0 || maxMs < 0
                || p50Ms < 0 || p95Ms < 0 || p99Ms < 0) {
            throw new IllegalArgumentException("latency values must not be negative");
        }
    }

    public static LatencySnapshot empty() {
        return new LatencySnapshot(0, 0, 0.0, 0, 0, 0, 0, 0);
    }
}
