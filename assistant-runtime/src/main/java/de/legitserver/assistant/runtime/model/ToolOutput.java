package de.legitserver.assistant.runtime.model;

import de.legitserver.assistant.core.classification.DataClassification;
import java.util.Map;
import java.util.Objects;

/** Trusted handler output before the mandatory runtime output filter. */
public record ToolOutput(Map<String, Object> values, DataClassification classification) {
    public ToolOutput {
        values = values == null ? Map.of() : Map.copyOf(values);
        classification = Objects.requireNonNull(classification, "classification");
    }
}
