package de.legitserver.assistant.core.error;

import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.LinkedHashMap;
import java.util.Map;

/** Safe transport error envelope. Details must never contain secrets. */
public record AssistantError(
        AssistantErrorCode errorCode,
        String publicMessage,
        RequestId requestId,
        boolean retryable,
        Map<String, String> details) {

    private static final int MAX_DETAILS = 16;

    public AssistantError {
        errorCode = CoreValidation.requireNonNull(errorCode, "errorCode");
        publicMessage = CoreValidation.requireText(publicMessage, "publicMessage", 512);
        requestId = CoreValidation.requireNonNull(requestId, "requestId");
        details = immutableDetails(details);
    }

    private static Map<String, String> immutableDetails(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        if (source.size() > MAX_DETAILS) {
            throw new IllegalArgumentException("details exceeds max entries " + MAX_DETAILS);
        }
        Map<String, String> copy = new LinkedHashMap<>();
        source.forEach(
                (key, value) ->
                        copy.put(
                                CoreValidation.requireSafeToken(key, "detail key", 64),
                                CoreValidation.requireText(value, "detail value", 256)));
        return Map.copyOf(copy);
    }
}
