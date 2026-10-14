package de.legitserver.assistant.runtime.model;

import de.legitserver.assistant.core.validation.CoreValidation;

/** Stable, non-command tool identifier. */
public record ToolId(String value) implements Comparable<ToolId> {
    public ToolId {
        value = CoreValidation.requireLowerDottedId(value, "toolId", 128);
        if (!value.startsWith("tool.")) {
            throw new IllegalArgumentException("toolId must use the tool.* namespace");
        }
    }

    @Override
    public int compareTo(ToolId other) {
        return value.compareTo(CoreValidation.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
