package de.legitserver.assistant.runtime.schema;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.Objects;

/** One strict, flat V1 tool schema field. */
public record ToolFieldSchema(
        String name,
        ToolValueType type,
        boolean required,
        int maxSize) {
    public ToolFieldSchema {
        name = CoreValidation.requireSafeToken(name, "schema field name", 96);
        type = Objects.requireNonNull(type, "type");
        if (maxSize < 0 || maxSize > 65536) throw new IllegalArgumentException("maxSize out of range");
        if ((type == ToolValueType.STRING || type == ToolValueType.STRING_LIST) && maxSize == 0) {
            throw new IllegalArgumentException("string/list schema fields require maxSize > 0");
        }
    }
}
