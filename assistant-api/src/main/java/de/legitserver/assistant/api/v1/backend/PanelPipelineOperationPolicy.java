package de.legitserver.assistant.api.v1.backend;

import de.legitserver.assistant.core.classification.DataClassification;
import java.time.Duration;

/** Server-owned transport policy for one Panel mutation operation. */
public record PanelPipelineOperationPolicy(
        DataClassification inputClassification,
        DataClassification responseClassificationCeiling,
        Duration timeout) {
    public PanelPipelineOperationPolicy {
        if (inputClassification == null || responseClassificationCeiling == null || timeout == null
                || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalArgumentException("invalid Panel pipeline operation policy");
        }
    }

    public static PanelPipelineOperationPolicy internalDefault() {
        return new PanelPipelineOperationPolicy(
                DataClassification.INTERNAL, DataClassification.INTERNAL, Duration.ofSeconds(30));
    }
}
