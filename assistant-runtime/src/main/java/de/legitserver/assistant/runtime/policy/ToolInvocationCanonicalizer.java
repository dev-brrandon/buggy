package de.legitserver.assistant.runtime.policy;

import de.legitserver.assistant.runtime.model.ToolInvocation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Deterministic approval payload for exact Phase-9 binding of a tool invocation. */
public final class ToolInvocationCanonicalizer {
    private ToolInvocationCanonicalizer() {}

    public static String canonicalPayload(ToolInvocation invocation) {
        StringBuilder out = new StringBuilder(256);
        out.append('{');
        field(out, "v", "tool-invocation-v1");
        out.append(','); field(out, "toolId", invocation.toolId().value());
        out.append(','); field(out, "resourceType", invocation.resource().resourceType());
        out.append(','); field(out, "resourceId", invocation.resource().resourceId());
        out.append(','); field(out, "resourceClassification", invocation.resource().classification().name());
        out.append(','); field(out, "inputClassification", invocation.inputClassification().name());
        out.append(',').append("\"input\":");
        map(out, invocation.input());
        out.append('}');
        return out.toString();
    }

    private static void map(StringBuilder out, Map<String, Object> values) {
        out.append('{');
        List<Map.Entry<String, Object>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));
        boolean first = true;
        for (Map.Entry<String, Object> entry : entries) {
            if (!first) out.append(',');
            first = false;
            string(out, entry.getKey());
            out.append(':');
            value(out, entry.getValue());
        }
        out.append('}');
    }

    private static void value(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            string(out, text);
        } else if (value instanceof Boolean || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long || value instanceof java.math.BigInteger) {
            out.append(value);
        } else if (value instanceof Number number) {
            out.append(new BigDecimal(number.toString()).stripTrailingZeros().toPlainString());
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) out.append(',');
                Object item = list.get(i);
                if (!(item instanceof String text)) throw new IllegalArgumentException("canonical list contains non-string");
                string(out, text);
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("unsupported canonical input type");
        }
    }

    private static void field(StringBuilder out, String name, String value) {
        string(out, name);
        out.append(':');
        string(out, value);
    }

    private static void string(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch));
                    else out.append(ch);
                }
            }
        }
        out.append('"');
    }
}
