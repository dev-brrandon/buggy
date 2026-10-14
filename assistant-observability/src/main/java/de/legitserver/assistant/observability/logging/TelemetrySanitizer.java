package de.legitserver.assistant.observability.logging;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Defense-in-depth sanitizer applied before structured logs and audit details leave the service. */
public final class TelemetrySanitizer {
    private static final int MAX_ENTRIES = 32;
    private static final int MAX_KEY_LENGTH = 96;
    private static final int MAX_VALUE_LENGTH = 1024;
    private static final String REDACTED = "[REDACTED]";
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            ".*(authorization|cookie|password|passwd|secret|token|api[-_.]?key|private[-_.]?key|credential).*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/-]{8,}=*");
    private static final Pattern URI_CREDENTIAL = Pattern.compile("(?i)([a-z][a-z0-9+.-]*://[^\\s:/]+:)[^@\\s]+(@)");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(password|passwd|secret|token|api[-_.]?key|credential)\\s*[=:]\\s*[^\\s,;]+"
    );
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}");
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]*");

    private TelemetrySanitizer() {}

    public static TelemetrySanitizer standard() {
        return new TelemetrySanitizer();
    }

    public Map<String, String> sanitizeMap(Map<String, String> input) {
        if (input == null || input.isEmpty()) return Map.of();
        LinkedHashMap<String, String> output = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<String, String> entry : input.entrySet()) {
            if (count >= MAX_ENTRIES) break;
            String key = sanitizeKey(entry.getKey());
            if (key == null || output.containsKey(key)) continue;
            output.put(key, SENSITIVE_KEY.matcher(key).matches() ? REDACTED : sanitizeValue(entry.getValue()));
            count++;
        }
        return Map.copyOf(output);
    }

    public String sanitizeValue(String value) {
        if (value == null) return "null";
        String sanitized = value.replaceAll("[\\p{Cntrl}]", " ").strip();
        if (sanitized.contains("-----BEGIN ") || sanitized.contains("-----END ")) return REDACTED;
        sanitized = BEARER.matcher(sanitized).replaceAll(REDACTED);
        sanitized = URI_CREDENTIAL.matcher(sanitized).replaceAll("$1" + REDACTED + "$2");
        sanitized = SECRET_ASSIGNMENT.matcher(sanitized).replaceAll("$1=" + REDACTED);
        sanitized = JWT.matcher(sanitized).replaceAll(REDACTED);
        if (sanitized.length() > MAX_VALUE_LENGTH) {
            sanitized = sanitized.substring(0, MAX_VALUE_LENGTH - 12) + "...[TRUNCATED]";
        }
        return sanitized;
    }

    public String sanitizeOperation(String value) {
        String sanitized = sanitizeValue(value);
        return sanitized.length() <= 192 ? sanitized : sanitized.substring(0, 192);
    }

    private static String sanitizeKey(String key) {
        if (key == null) return null;
        String normalized = key.strip();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > MAX_KEY_LENGTH) normalized = normalized.substring(0, MAX_KEY_LENGTH);
        if (!SAFE_KEY.matcher(normalized).matches()) {
            normalized = normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._:-]", "_");
        }
        return normalized.isEmpty() ? null : normalized;
    }
}
