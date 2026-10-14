package de.legitserver.assistant.supervisor.health;

import de.legitserver.assistant.runtime.model.ToolId;
import java.time.Instant;

/** Bounded aggregate of recent failures for one registered tool. */
public record ToolFailureSummary(
        ToolId toolId,
        long failures,
        long timeouts,
        String lastSafeCode,
        Instant lastFailureAt) {
    public ToolFailureSummary {
        if (toolId == null) throw new IllegalArgumentException("toolId must not be null");
        if (failures < 0 || timeouts < 0) throw new IllegalArgumentException("failure counts must not be negative");
        if (failures > 0 && (lastSafeCode == null || lastSafeCode.isBlank() || lastFailureAt == null)) {
            throw new IllegalArgumentException("failed tool summary requires last failure details");
        }
    }
}
