package de.legitserver.assistant.runtime.schema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Strict validator used for both tool inputs and tool outputs. */
public final class ToolSchemaValidator {
    private static final int MAX_TOTAL_UTF8_BYTES = 262_144;

    public void validate(ToolSchema schema, Map<String, ?> values) {
        if (schema == null || values == null) throw new ToolSchemaViolationException("SCHEMA_OR_VALUES_MISSING");
        if (values.size() > schema.fields().size()) {
            for (String key : values.keySet()) {
                if (!schema.fields().containsKey(key)) throw new ToolSchemaViolationException("UNKNOWN_FIELD");
            }
        }
        for (String required : schema.requiredFields()) {
            if (!values.containsKey(required) || values.get(required) == null) {
                throw new ToolSchemaViolationException("REQUIRED_FIELD_MISSING");
            }
        }
        int bytes = 0;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            ToolFieldSchema field = schema.fields().get(entry.getKey());
            if (field == null) throw new ToolSchemaViolationException("UNKNOWN_FIELD");
            Object value = entry.getValue();
            if (value == null) {
                if (field.required()) throw new ToolSchemaViolationException("REQUIRED_FIELD_NULL");
                continue;
            }
            bytes += validateValue(field, value);
            if (bytes > MAX_TOTAL_UTF8_BYTES) throw new ToolSchemaViolationException("SCHEMA_PAYLOAD_TOO_LARGE");
        }
    }

    private static int validateValue(ToolFieldSchema field, Object value) {
        return switch (field.type()) {
            case STRING -> validateString(field, value);
            case BOOLEAN -> require(value instanceof Boolean, "TYPE_BOOLEAN");
            case INTEGER -> validateInteger(value);
            case DECIMAL -> validateDecimal(value);
            case STRING_LIST -> validateStringList(field, value);
        };
    }

    private static int validateString(ToolFieldSchema field, Object value) {
        if (!(value instanceof String text)) throw new ToolSchemaViolationException("TYPE_STRING");
        if (text.length() > field.maxSize()) throw new ToolSchemaViolationException("STRING_TOO_LONG");
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static int validateInteger(Object value) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof BigInteger)) {
            throw new ToolSchemaViolationException("TYPE_INTEGER");
        }
        return value.toString().length();
    }

    private static int validateDecimal(Object value) {
        if (!(value instanceof Number || value instanceof BigDecimal)) throw new ToolSchemaViolationException("TYPE_DECIMAL");
        if (value instanceof Double d && !Double.isFinite(d)) throw new ToolSchemaViolationException("DECIMAL_NON_FINITE");
        if (value instanceof Float f && !Float.isFinite(f)) throw new ToolSchemaViolationException("DECIMAL_NON_FINITE");
        return value.toString().length();
    }

    private static int validateStringList(ToolFieldSchema field, Object value) {
        if (!(value instanceof List<?> list)) throw new ToolSchemaViolationException("TYPE_STRING_LIST");
        if (list.size() > field.maxSize()) throw new ToolSchemaViolationException("LIST_TOO_LONG");
        int bytes = 0;
        for (Object item : list) {
            if (!(item instanceof String text)) throw new ToolSchemaViolationException("LIST_ITEM_NOT_STRING");
            if (text.length() > 4096) throw new ToolSchemaViolationException("LIST_ITEM_TOO_LONG");
            bytes += text.getBytes(StandardCharsets.UTF_8).length;
        }
        return bytes;
    }

    private static int require(boolean condition, String code) {
        if (!condition) throw new ToolSchemaViolationException(code);
        return 1;
    }
}
