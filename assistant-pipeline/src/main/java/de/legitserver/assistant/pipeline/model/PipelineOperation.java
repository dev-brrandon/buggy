package de.legitserver.assistant.pipeline.model;

import de.legitserver.assistant.core.validation.CoreValidation;

/** Stable internal operation identifier resolved to a capability and route server-side. */
public record PipelineOperation(String value) implements Comparable<PipelineOperation> {
    public PipelineOperation {
        value = CoreValidation.requireLowerDottedId(value, "operation", 128);
        if (!value.startsWith("assistant.")) {
            throw new IllegalArgumentException("operation must use the assistant.* namespace");
        }
    }

    @Override
    public int compareTo(PipelineOperation other) {
        return value.compareTo(CoreValidation.requireNonNull(other, "other").value);
    }
}
