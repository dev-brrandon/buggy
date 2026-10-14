package de.legitserver.assistant.buggy.controlcenter;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded transport-neutral read projection for the Buggy Control Center. */
public record BuggyControlCenterResource(String id, Map<String, Object> attributes) {
    public BuggyControlCenterResource {
        id = CoreValidation.requireSafeToken(id, "id", 192);
        LinkedHashMap<String, Object> safe = new LinkedHashMap<>();
        if (attributes != null) {
            if (attributes.size() > 128) throw new IllegalArgumentException("control-center attributes exceed 128 entries");
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                String key = CoreValidation.requireSafeToken(entry.getKey(), "attribute key", 96);
                Object normalized = normalize(entry.getValue());
                if (normalized != null) safe.put(key, normalized);
            }
        }
        attributes = Map.copyOf(safe);
    }

    private static Object normalize(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) return value;
        if (value instanceof Enum<?> enumeration) return enumeration.name();
        if (value instanceof java.time.Instant instant) return instant.toString();
        if (value instanceof java.time.Duration duration) return duration.toString();
        if (value instanceof java.util.UUID uuid) return uuid.toString();
        if (value instanceof String text) {
            if (text.length() > 16_384 || text.indexOf('\0') >= 0) throw new IllegalArgumentException("attribute text invalid");
            return text;
        }
        if (value instanceof java.util.Collection<?> collection) {
            if (collection.size() > 256) throw new IllegalArgumentException("attribute collection too large");
            return collection.stream().map(BuggyControlCenterResource::normalize).toList();
        }
        if (value instanceof Map<?, ?> map) {
            if (map.size() > 128) throw new IllegalArgumentException("nested attribute map too large");
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("nested map key must be String");
                Object nested = normalize(entry.getValue());
                if (nested != null) out.put(CoreValidation.requireSafeToken(key, "nested attribute key", 96), nested);
            }
            return Map.copyOf(out);
        }
        return value.toString();
    }
}
