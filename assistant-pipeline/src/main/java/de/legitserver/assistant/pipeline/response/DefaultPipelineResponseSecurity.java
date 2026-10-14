package de.legitserver.assistant.pipeline.response;

import de.legitserver.assistant.pipeline.model.PipelineExecutionContext;
import de.legitserver.assistant.pipeline.model.PipelineExecutionOutput;
import de.legitserver.assistant.pipeline.port.PipelineResponseSecurityPort;
import de.legitserver.assistant.security.secret.SecretGuard;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Enforces response classification and recursively redacts recognized secrets. */
public final class DefaultPipelineResponseSecurity implements PipelineResponseSecurityPort {
    private static final int MAX_DEPTH = 8;
    private static final int MAX_ENTRIES = 256;
    private final SecretGuard secrets = new SecretGuard();

    @Override
    public PipelineExecutionOutput secure(PipelineExecutionContext context, PipelineExecutionOutput output) {
        if (context == null || output == null) throw new PipelineResponseSecurityException("RESPONSE_INVALID");
        if (output.classification().isAtLeast(context.request().responseClassificationCeiling())
                && output.classification() != context.request().responseClassificationCeiling()) {
            throw new PipelineResponseSecurityException("OUTPUT_CLASSIFICATION_DENIED");
        }
        Object sanitized = sanitize(output.values(), 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) sanitized;
        return new PipelineExecutionOutput(values, output.classification());
    }

    private Object sanitize(Object value, int depth) {
        if (depth > MAX_DEPTH) throw new PipelineResponseSecurityException("OUTPUT_DEPTH_EXCEEDED");
        if (value == null || value instanceof Boolean || value instanceof Number) return value;
        if (value instanceof String text) return secrets.redact(text).redactedText();
        if (value instanceof Map<?, ?> map) {
            if (map.size() > MAX_ENTRIES) throw new PipelineResponseSecurityException("OUTPUT_SIZE_EXCEEDED");
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw new PipelineResponseSecurityException("OUTPUT_KEY_INVALID");
                copy.put(key, sanitize(entry.getValue(), depth + 1));
            }
            return Map.copyOf(copy);
        }
        if (value instanceof List<?> list) {
            if (list.size() > MAX_ENTRIES) throw new PipelineResponseSecurityException("OUTPUT_SIZE_EXCEEDED");
            ArrayList<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) copy.add(sanitize(item, depth + 1));
            return List.copyOf(copy);
        }
        throw new PipelineResponseSecurityException("OUTPUT_TYPE_INVALID");
    }
}
