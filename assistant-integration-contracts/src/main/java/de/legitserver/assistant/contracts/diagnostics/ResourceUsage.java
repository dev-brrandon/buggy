package de.legitserver.assistant.contracts.diagnostics;

import java.time.Instant;
import java.util.Objects;

public record ResourceUsage(double cpuPercent, long memoryBytes, long diskBytes, Instant observedAt) {
    public ResourceUsage {
        if (!Double.isFinite(cpuPercent) || cpuPercent < 0.0 || cpuPercent > 100.0) {
            throw new IllegalArgumentException("cpuPercent must be 0..100");
        }
        if (memoryBytes < 0 || diskBytes < 0) throw new IllegalArgumentException("resource bytes must be >= 0");
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
    }
}
