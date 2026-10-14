package de.legitserver.assistant.runtime.model;

import de.legitserver.assistant.core.classification.DataClassification;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** Sanitized successful tool result returned across the runtime boundary. */
public record ToolInvocationResult(
        ToolId toolId,
        Map<String, Object> output,
        DataClassification classification,
        Duration duration) {
    public ToolInvocationResult {
        toolId = Objects.requireNonNull(toolId, "toolId");
        output = output == null ? Map.of() : Map.copyOf(output);
        classification = Objects.requireNonNull(classification, "classification");
        duration = Objects.requireNonNull(duration, "duration");
    }
}
