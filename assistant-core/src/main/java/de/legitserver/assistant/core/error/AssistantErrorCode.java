package de.legitserver.assistant.core.error;

import de.legitserver.assistant.core.validation.CoreValidation;

/** Stable machine-readable error code safe for transport contracts. */
public record AssistantErrorCode(String value) {
    public AssistantErrorCode {
        value = CoreValidation.requireUpperCode(value, "errorCode", 96);
    }

    @Override
    public String toString() {
        return value;
    }
}
