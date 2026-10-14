package de.legitserver.assistant.api.v1.model;

import java.util.Map;

/** Bounded API command payload. Domain validation remains owned by the receiving application/domain service. */
public record ApiCommandRequest(Map<String, Object> data) {
    private static final int MAX_FIELDS = 64;

    public ApiCommandRequest {
        data = data == null ? Map.of() : Map.copyOf(data);
        if (data.size() > MAX_FIELDS) throw new IllegalArgumentException("request body exceeds max fields " + MAX_FIELDS);
    }
}
