package de.legitserver.assistant.core.request;

import de.legitserver.assistant.core.validation.CoreValidation;

/** Opaque distributed-tracing identifier. */
public record TraceId(String value) {
    public TraceId {
        value = CoreValidation.requireSafeToken(value, "traceId", 128);
    }

    @Override
    public String toString() {
        return value;
    }
}
