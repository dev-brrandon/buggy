package de.legitserver.assistant.conversations.validation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared bounded validation helpers for conversation values. */
public final class ConversationValidation {
    private ConversationValidation() {}

    public static String requireContent(String value, int maxUtf8Bytes) {
        if (value == null) throw new IllegalArgumentException("content must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("content must not be blank");
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > maxUtf8Bytes) {
            throw new IllegalArgumentException("content exceeds max UTF-8 bytes: " + maxUtf8Bytes);
        }
        return value;
    }

    public static Map<String, String> boundedMetadata(
            Map<String, String> metadata, int maxEntries, int maxUtf8Bytes) {
        if (metadata == null || metadata.isEmpty()) return Map.of();
        if (metadata.size() > maxEntries) {
            throw new IllegalArgumentException("metadata exceeds max entries: " + maxEntries);
        }
        Map<String, String> copy = new LinkedHashMap<>();
        int bytes = 2;
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String key = requireToken(entry.getKey(), "metadata key", 128);
            String value = entry.getValue();
            if (value == null) throw new IllegalArgumentException("metadata value must not be null");
            if (value.length() > 4096) throw new IllegalArgumentException("metadata value too long: " + key);
            bytes += key.getBytes(StandardCharsets.UTF_8).length;
            bytes += value.getBytes(StandardCharsets.UTF_8).length;
            bytes += 6;
            copy.put(key, value);
        }
        if (bytes > maxUtf8Bytes) {
            throw new IllegalArgumentException("metadata exceeds max UTF-8 bytes: " + maxUtf8Bytes);
        }
        return Collections.unmodifiableMap(copy);
    }

    public static String requireSha256(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
        }
        return value;
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String requireToken(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        if (value.length() > maxLength) throw new IllegalArgumentException(field + " too long");
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) throw new IllegalArgumentException(field + " contains control characters");
        }
        return value;
    }
}
