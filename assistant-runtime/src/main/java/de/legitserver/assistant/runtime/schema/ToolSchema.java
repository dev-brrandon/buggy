package de.legitserver.assistant.runtime.schema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict V1 schema: flat primitive fields, no unknown-field acceptance. */
public record ToolSchema(Map<String, ToolFieldSchema> fields) {
    public ToolSchema {
        if (fields == null) throw new IllegalArgumentException("fields must not be null");
        if (fields.size() > 64) throw new IllegalArgumentException("schema exceeds 64 fields");
        LinkedHashMap<String, ToolFieldSchema> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ToolFieldSchema> entry : fields.entrySet()) {
            ToolFieldSchema field = entry.getValue();
            if (field == null || !field.name().equals(entry.getKey())) {
                throw new IllegalArgumentException("schema map key must equal field name");
            }
            if (copy.put(entry.getKey(), field) != null) throw new IllegalArgumentException("duplicate field");
        }
        fields = Map.copyOf(copy);
    }

    public static ToolSchema of(ToolFieldSchema... fields) {
        LinkedHashMap<String, ToolFieldSchema> map = new LinkedHashMap<>();
        if (fields != null) {
            for (ToolFieldSchema field : fields) {
                if (field == null || map.put(field.name(), field) != null) {
                    throw new IllegalArgumentException("null or duplicate field");
                }
            }
        }
        return new ToolSchema(map);
    }

    public static ToolSchema empty() {
        return new ToolSchema(Map.of());
    }

    public List<String> requiredFields() {
        return fields.values().stream().filter(ToolFieldSchema::required).map(ToolFieldSchema::name).sorted().toList();
    }
}
