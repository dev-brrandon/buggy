package de.legitserver.assistant.knowledge.validation;

import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.knowledge.policy.KnowledgeLimits;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Bounded validation and canonicalization for Knowledge values. */
public final class KnowledgeValidation {
    private KnowledgeValidation() {}

    public static String content(String value) {
        if (value == null) throw new IllegalArgumentException("content must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("content must not be blank");
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > KnowledgeLimits.MAX_CONTENT_UTF8_BYTES) {
            throw new IllegalArgumentException(
                    "content exceeds max UTF-8 bytes: " + KnowledgeLimits.MAX_CONTENT_UTF8_BYTES);
        }
        return value;
    }

    public static Set<String> tags(Set<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        if (values.size() > KnowledgeLimits.MAX_TAGS) {
            throw new IllegalArgumentException("tags exceed max entries: " + KnowledgeLimits.MAX_TAGS);
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = CoreValidation.requireSafeToken(
                            value, "tag", KnowledgeLimits.MAX_TAG_CHARS)
                    .toLowerCase(Locale.ROOT);
            result.add(normalized);
        }
        return Collections.unmodifiableSet(result);
    }

    public static Set<String> permissions(Set<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        if (values.size() > KnowledgeLimits.MAX_ACL_PERMISSIONS) {
            throw new IllegalArgumentException(
                    "ACL permissions exceed max entries: " + KnowledgeLimits.MAX_ACL_PERMISSIONS);
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            result.add(CoreValidation.requireSafeToken(
                    value, "ACL permission", KnowledgeLimits.MAX_PERMISSION_CHARS));
        }
        return Collections.unmodifiableSet(result);
    }

    public static Map<String, String> metadata(Map<String, String> values, int maxEntries, int maxUtf8Bytes) {
        if (values == null || values.isEmpty()) return Map.of();
        if (values.size() > maxEntries) {
            throw new IllegalArgumentException("metadata exceeds max entries: " + maxEntries);
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        int bytes = 2;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = CoreValidation.requireSafeToken(entry.getKey(), "metadata key", 128);
            String value = CoreValidation.requireText(entry.getValue(), "metadata value", 4096);
            bytes += key.getBytes(StandardCharsets.UTF_8).length;
            bytes += value.getBytes(StandardCharsets.UTF_8).length;
            bytes += 6;
            result.put(key, value);
        }
        if (bytes > maxUtf8Bytes) {
            throw new IllegalArgumentException("metadata exceeds max UTF-8 bytes: " + maxUtf8Bytes);
        }
        return Collections.unmodifiableMap(result);
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
}
