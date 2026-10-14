package de.legitserver.assistant.bugs.model;

import java.util.Objects;
import java.util.UUID;

/** Stable internal identifier for a Bug aggregate. */
public record InternalBugId(UUID value) {
    public InternalBugId {
        Objects.requireNonNull(value, "value");
    }

    public static InternalBugId random() {
        return new InternalBugId(UUID.randomUUID());
    }
}
