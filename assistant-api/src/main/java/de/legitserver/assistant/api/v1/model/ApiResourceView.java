package de.legitserver.assistant.api.v1.model;

import java.util.Map;

/** Stable JSON object returned by domain-specific API backends without leaking infrastructure types. */
public record ApiResourceView(String id, Map<String, Object> attributes) {
    public ApiResourceView {
        if (id == null || id.isBlank() || id.length() > 192) throw new IllegalArgumentException("id invalid");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        if (attributes.size() > 128) throw new IllegalArgumentException("attributes exceed max entries 128");
    }
}
