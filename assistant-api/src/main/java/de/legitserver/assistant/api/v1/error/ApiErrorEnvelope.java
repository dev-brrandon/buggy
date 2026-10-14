package de.legitserver.assistant.api.v1.error;

import java.util.Map;

/** Unified public error model for every /api/assistant/v1 endpoint. */
public record ApiErrorEnvelope(
        String errorCode,
        String message,
        String requestId,
        boolean retryable,
        Map<String, String> details) {
    public ApiErrorEnvelope {
        if (errorCode == null || errorCode.isBlank()) throw new IllegalArgumentException("errorCode required");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message required");
        if (requestId == null || requestId.isBlank()) throw new IllegalArgumentException("requestId required");
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
