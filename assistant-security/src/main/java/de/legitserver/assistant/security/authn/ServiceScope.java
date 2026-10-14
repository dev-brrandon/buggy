package de.legitserver.assistant.security.authn;

import de.legitserver.assistant.core.validation.CoreValidation;

/** Stable, lower-case dotted service scope granted to an authenticated internal caller. */
public record ServiceScope(String value) implements Comparable<ServiceScope> {
    public ServiceScope {
        value = CoreValidation.requireLowerDottedId(value, "serviceScope", 128);
    }

    @Override
    public int compareTo(ServiceScope other) {
        return value.compareTo(CoreValidation.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value;
    }
}
