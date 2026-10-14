package de.legitserver.assistant.core.validation;

import java.util.Objects;
import java.util.regex.Pattern;

/** Shared validation primitives for framework-free core value objects. */
public final class CoreValidation {

    private static final Pattern SAFE_TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]*");
    private static final Pattern LOWER_DOTTED_ID = Pattern.compile("[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+");
    private static final Pattern UPPER_CODE = Pattern.compile("[A-Z][A-Z0-9_]*(\\.[A-Z][A-Z0-9_]*)*");

    private CoreValidation() {}

    public static <T> T requireNonNull(T value, String field) {
        return Objects.requireNonNull(value, field + " must not be null");
    }

    public static String requireText(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds max length " + maxLength);
        }
        if (containsControl(normalized)) {
            throw new IllegalArgumentException(field + " contains control characters");
        }
        return normalized;
    }

    public static String requireSafeToken(String value, String field, int maxLength) {
        String normalized = requireText(value, field, maxLength);
        if (!SAFE_TOKEN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " is not a safe token");
        }
        return normalized;
    }

    public static String requireLowerDottedId(String value, String field, int maxLength) {
        String normalized = requireText(value, field, maxLength);
        if (!LOWER_DOTTED_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must be a lower-case dotted identifier");
        }
        return normalized;
    }

    public static String requireUpperCode(String value, String field, int maxLength) {
        String normalized = requireText(value, field, maxLength);
        if (!UPPER_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must be an upper-case error code");
        }
        return normalized;
    }

    public static String optionalText(String value, String field, int maxLength) {
        return value == null ? null : requireText(value, field, maxLength);
    }

    public static String optionalSafeToken(String value, String field, int maxLength) {
        return value == null ? null : requireSafeToken(value, field, maxLength);
    }

    private static boolean containsControl(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }
}
