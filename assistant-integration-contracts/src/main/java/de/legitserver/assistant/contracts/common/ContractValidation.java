package de.legitserver.assistant.contracts.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/** Shared strict validation for Phase-12 integration contracts. */
public final class ContractValidation {
    private ContractValidation() {}

    public static String id(String value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 160) {
            throw new IllegalArgumentException(field + " must contain 1..160 characters");
        }
        if (!normalized.matches("[A-Za-z0-9][A-Za-z0-9._:/-]*")) {
            throw new IllegalArgumentException(field + " contains unsupported characters");
        }
        return normalized;
    }

    public static String text(String value, String field, int maxLength) {
        if (value == null) throw new IllegalArgumentException(field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must contain 1.." + maxLength + " characters");
        }
        if (normalized.indexOf('\0') >= 0) throw new IllegalArgumentException(field + " contains NUL");
        return normalized;
    }

    public static String optionalText(String value, String field, int maxLength) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        if (normalized.indexOf('\0') >= 0) throw new IllegalArgumentException(field + " contains NUL");
        return normalized;
    }

    public static String contentType(String value) {
        String normalized = text(value, "contentType", 160).toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+(?:;[a-z0-9._=+ -]+)*")) {
            throw new IllegalArgumentException("contentType is invalid");
        }
        return normalized;
    }

    public static String sha256(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("sha256 must be exactly 64 hexadecimal characters");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    public static String sha256Of(String value) {
        if (value == null) throw new IllegalArgumentException("value must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public static Map<String, String> metadata(Map<String, String> metadata, int maxEntries) {
        if (metadata == null || metadata.isEmpty()) return Map.of();
        if (metadata.size() > maxEntries) throw new IllegalArgumentException("too many metadata entries");
        java.util.LinkedHashMap<String, String> copy = new java.util.LinkedHashMap<>();
        metadata.forEach((key, value) -> copy.put(id(key, "metadata key"), text(value, "metadata value", 1024)));
        return Map.copyOf(copy);
    }
}
