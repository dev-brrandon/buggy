package de.legitserver.assistant.bugs.model;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.Locale;

/** Public, stable Bug identifier such as FARM-0027. */
public record PublicBugId(String value) {
    public PublicBugId {
        value = CoreValidation.requireSafeToken(value, "publicBugId", 64).toUpperCase(Locale.ROOT);
    }

    public static PublicBugId of(String value) {
        return new PublicBugId(value);
    }
}
