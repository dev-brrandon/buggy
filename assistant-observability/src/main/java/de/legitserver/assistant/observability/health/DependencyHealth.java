package de.legitserver.assistant.observability.health;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;

/** Sanitized dependency health sample without raw exception or connection details. */
public record DependencyHealth(
        DependencyId dependency,
        DependencyState state,
        String safeCode,
        long latencyMs,
        Instant checkedAt) {

    public DependencyHealth {
        dependency = CoreValidation.requireNonNull(dependency, "dependency");
        state = CoreValidation.requireNonNull(state, "state");
        safeCode = CoreValidation.requireUpperCode(safeCode, "safeCode", 96);
        if (latencyMs < 0) throw new IllegalArgumentException("latencyMs must not be negative");
        checkedAt = CoreValidation.requireNonNull(checkedAt, "checkedAt");
    }

    public static DependencyHealth notConfigured(DependencyId dependency, Instant now) {
        return new DependencyHealth(dependency, DependencyState.NOT_CONFIGURED, "NOT_CONFIGURED", 0L, now);
    }
}
