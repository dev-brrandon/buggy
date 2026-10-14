package de.legitserver.assistant.runtime.execution;

import de.legitserver.assistant.runtime.model.ToolOutput;
import de.legitserver.assistant.security.secret.SecretGuard;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mandatory defense-in-depth output redaction before any tool output crosses the runtime boundary. */
public final class ToolOutputFilter {
    private final SecretGuard secretGuard;

    public ToolOutputFilter(SecretGuard secretGuard) {
        if (secretGuard == null) throw new IllegalArgumentException("secretGuard must not be null");
        this.secretGuard = secretGuard;
    }

    public ToolOutput sanitize(ToolOutput output) {
        LinkedHashMap<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : output.values().entrySet()) {
            sanitized.put(entry.getKey(), sanitizeValue(entry.getValue()));
        }
        return new ToolOutput(Map.copyOf(sanitized), output.classification());
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof String text) return secretGuard.redact(text).redactedText();
        if (value instanceof List<?> list) {
            List<Object> sanitized = new ArrayList<>(list.size());
            for (Object item : list) {
                if (!(item instanceof String text)) return item;
                sanitized.add(secretGuard.redact(text).redactedText());
            }
            return List.copyOf(sanitized);
        }
        return value;
    }
}
