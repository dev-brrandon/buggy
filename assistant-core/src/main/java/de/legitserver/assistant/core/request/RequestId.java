package de.legitserver.assistant.core.request;

import de.legitserver.assistant.core.validation.CoreValidation;

/** Opaque request correlation identifier. */
public record RequestId(String value) {
    public RequestId {
        value = CoreValidation.requireSafeToken(value, "requestId", 128);
    }

    @Override
    public String toString() {
        return value;
    }
}
