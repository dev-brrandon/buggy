package de.legitserver.assistant.pipeline.control;

import de.legitserver.assistant.pipeline.model.PipelineRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Deterministic bounded canonicalization used only for idempotency binding. */
public final class PipelineCanonicalizer {
    private static final int MAX_DEPTH = 8;
    private static final int MAX_ENTRIES = 256;
    private static final int MAX_TEXT = 16_384;
    private static final int MAX_CANONICAL_BYTES = 512 * 1024;

    private PipelineCanonicalizer() {}

    public static String requestHash(PipelineRequest request) {
        if (request == null) throw new IllegalArgumentException("request must not be null");
        StringBuilder builder = new StringBuilder();
        append(builder, request.assistant().assistantId().name());
        append(builder, Long.toString(request.assistant().revision()));
        append(builder, request.operation().value());
        append(builder, request.context().conversationId() == null ? "-" : request.context().conversationId());
        append(builder, request.context().serverId() == null ? "-" : request.context().serverId());
        append(builder, request.resource().resourceType());
        append(builder, request.resource().resourceId());
        append(builder, request.resource().classification().name());
        append(builder, request.inputClassification().name());
        append(builder, request.responseClassificationCeiling().name());
        append(builder, request.approvalRequestId() == null ? "-" : request.approvalRequestId().toString());
        canonicalValue(builder, request.input(), 0, new Counter());
        byte[] bytes = builder.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_CANONICAL_BYTES) throw new IllegalArgumentException("canonical request exceeds limit");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static void canonicalValue(StringBuilder out, Object value, int depth, Counter counter) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("input nesting exceeds limit");
        if (value == null) {
            append(out, "null");
        } else if (value instanceof String text) {
            if (text.length() > MAX_TEXT) throw new IllegalArgumentException("input text exceeds limit");
            append(out, "s:" + text);
        } else if (value instanceof Boolean || value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double) {
            append(out, "n:" + value);
        } else if (value instanceof Map<?, ?> map) {
            if (map.size() > MAX_ENTRIES) throw new IllegalArgumentException("input map exceeds limit");
            List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
            entries.sort(Comparator.comparing(entry -> String.valueOf(entry.getKey())));
            append(out, "map:" + entries.size());
            for (Map.Entry<?, ?> entry : entries) {
                counter.add();
                if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("input map keys must be strings");
                append(out, "k:" + key);
                canonicalValue(out, entry.getValue(), depth + 1, counter);
            }
        } else if (value instanceof List<?> list) {
            if (list.size() > MAX_ENTRIES) throw new IllegalArgumentException("input list exceeds limit");
            append(out, "list:" + list.size());
            for (Object item : list) {
                counter.add();
                canonicalValue(out, item, depth + 1, counter);
            }
        } else {
            throw new IllegalArgumentException("unsupported input value type");
        }
    }

    private static void append(StringBuilder builder, String value) {
        String safe = value == null ? "null" : value;
        builder.append(safe.length()).append(':').append(safe).append('\n');
    }

    private static final class Counter {
        private int value;
        private void add() {
            value++;
            if (value > MAX_ENTRIES * 4) throw new IllegalArgumentException("input entry count exceeds limit");
        }
    }
}
