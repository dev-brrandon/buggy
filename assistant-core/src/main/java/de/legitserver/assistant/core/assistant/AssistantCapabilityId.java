package de.legitserver.assistant.core.assistant;

import de.legitserver.assistant.core.validation.CoreValidation;

/** Extensible, stable capability identifier. */
public record AssistantCapabilityId(String value) implements Comparable<AssistantCapabilityId> {

    public AssistantCapabilityId {
        value = CoreValidation.requireLowerDottedId(value, "capabilityId", 128);
        if (!value.startsWith("assistant.")) {
            throw new IllegalArgumentException("capabilityId must use the assistant.* namespace");
        }
    }

    @Override
    public int compareTo(AssistantCapabilityId other) {
        return value.compareTo(CoreValidation.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
