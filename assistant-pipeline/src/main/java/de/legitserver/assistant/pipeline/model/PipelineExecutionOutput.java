package de.legitserver.assistant.pipeline.model;

import de.legitserver.assistant.core.classification.DataClassification;
import java.util.Map;
import java.util.Objects;

/** Typed internal execution output before response security and formatting. */
public record PipelineExecutionOutput(Map<String, Object> values, DataClassification classification) {
    public PipelineExecutionOutput {
        values = values == null ? Map.of() : Map.copyOf(values);
        classification = Objects.requireNonNull(classification, "classification");
    }
}
