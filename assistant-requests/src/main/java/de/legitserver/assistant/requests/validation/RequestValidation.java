package de.legitserver.assistant.requests.validation;

import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.requests.policy.RequestLimits;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded validation and exact-payload hashing primitives for requests. */
public final class RequestValidation {
    private RequestValidation() {}

    public static String requestType(String value) {
        return CoreValidation.requireSafeToken(value, "requestType", RequestLimits.MAX_REQUEST_TYPE_CHARS);
    }

    public static String contentType(String value) {
        return CoreValidation.requireText(value, "contentType", RequestLimits.MAX_CONTENT_TYPE_CHARS);
    }

    public static String payloadBody(String value) {
        if (value == null) throw new IllegalArgumentException("payloadBody must not be null");
        if (value.getBytes(StandardCharsets.UTF_8).length > RequestLimits.MAX_PAYLOAD_UTF8_BYTES) {
            throw new IllegalArgumentException("payloadBody exceeds max UTF-8 bytes: " + RequestLimits.MAX_PAYLOAD_UTF8_BYTES);
        }
        return value;
    }

    public static String reason(String value) {
        return value == null ? null : CoreValidation.requireText(value, "reason", RequestLimits.MAX_REASON_CHARS);
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

    public static Map<String, String> historyDetails(Map<String, String> values) {
        if (values == null || values.isEmpty()) return Map.of();
        if (values.size() > RequestLimits.MAX_HISTORY_DETAILS_ENTRIES) {
            throw new IllegalArgumentException("historyDetails exceeds max entries: " + RequestLimits.MAX_HISTORY_DETAILS_ENTRIES);
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        int bytes = 2;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = CoreValidation.requireSafeToken(entry.getKey(), "historyDetails key", 128);
            String val = CoreValidation.requireText(entry.getValue(), "historyDetails value", 4096);
            bytes += key.getBytes(StandardCharsets.UTF_8).length;
            bytes += val.getBytes(StandardCharsets.UTF_8).length;
            bytes += 6;
            result.put(key, val);
        }
        if (bytes > RequestLimits.MAX_HISTORY_DETAILS_UTF8_BYTES) {
            throw new IllegalArgumentException("historyDetails exceeds max UTF-8 bytes: "
                    + RequestLimits.MAX_HISTORY_DETAILS_UTF8_BYTES);
        }
        return Collections.unmodifiableMap(result);
    }
}
