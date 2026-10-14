package de.legitserver.assistant.buggy.tasks;

import java.util.Objects;
import java.util.UUID;

public record BuggyTaskId(UUID value) {
    public BuggyTaskId {
        value = Objects.requireNonNull(value, "value");
    }
    public static BuggyTaskId random() { return new BuggyTaskId(UUID.randomUUID()); }
    @Override public String toString() { return value.toString(); }
}
