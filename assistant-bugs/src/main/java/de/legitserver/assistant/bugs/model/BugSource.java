package de.legitserver.assistant.bugs.model;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.Locale;

/** Extensible origin token for Bug producers such as PANEL, MINECRAFT or ASSISTANT. */
public record BugSource(String value) {
    public BugSource {
        value = CoreValidation.requireSafeToken(value, "source", 64).toUpperCase(Locale.ROOT);
    }

    public static BugSource of(String value) {
        return new BugSource(value);
    }
}
