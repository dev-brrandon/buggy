package de.legitserver.assistant.bugs.validation;

import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.bugs.policy.BugLimits;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded validation and hashing for Bug domain values. */
public final class BugValidation {
    private BugValidation() {}

    public static String originalReport(String value) {
        return boundedText(value, "originalReport", BugLimits.MAX_ORIGINAL_REPORT_UTF8_BYTES);
    }

    public static String assistantSummary(String value) {
        return value == null ? null : boundedText(value, "assistantSummary", BugLimits.MAX_ASSISTANT_SUMMARY_UTF8_BYTES);
    }

    public static Map<String, String> metadata(Map<String, String> values) {
        return boundedMap(values, BugLimits.MAX_METADATA_ENTRIES, BugLimits.MAX_METADATA_UTF8_BYTES, "metadata");
    }

    public static Map<String, String> historyMap(Map<String, String> values) {
        return boundedMap(values, BugLimits.MAX_HISTORY_MAP_ENTRIES, BugLimits.MAX_HISTORY_MAP_UTF8_BYTES, "history");
    }

    public static String reason(String value) {
        return value == null ? null : CoreValidation.requireText(value, "reason", BugLimits.MAX_REASON_CHARS);
    }

    public static String sha256(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
        }
        return value;
    }

    public static String sha256Of(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String boundedText(String value, String field, int maxUtf8Bytes) {
        if (value == null) throw new IllegalArgumentException(field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        if (value.getBytes(StandardCharsets.UTF_8).length > maxUtf8Bytes) {
            throw new IllegalArgumentException(field + " exceeds max UTF-8 bytes: " + maxUtf8Bytes);
        }
        return value;
    }

    private static Map<String, String> boundedMap(
            Map<String, String> values, int maxEntries, int maxUtf8Bytes, String field) {
        if (values == null || values.isEmpty()) return Map.of();
        if (values.size() > maxEntries) {
            throw new IllegalArgumentException(field + " exceeds max entries: " + maxEntries);
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        int bytes = 2;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = CoreValidation.requireSafeToken(entry.getKey(), field + " key", 128);
            String value = CoreValidation.requireText(entry.getValue(), field + " value", 4096);
            bytes += key.getBytes(StandardCharsets.UTF_8).length;
            bytes += value.getBytes(StandardCharsets.UTF_8).length;
            bytes += 6;
            result.put(key, value);
        }
        if (bytes > maxUtf8Bytes) {
            throw new IllegalArgumentException(field + " exceeds max UTF-8 bytes: " + maxUtf8Bytes);
        }
        return Collections.unmodifiableMap(result);
    }
}
