package de.legitserver.assistant.buggy.memory;

import java.util.Objects;
import java.util.UUID;

public record BuggyMemoryId(UUID value) implements Comparable<BuggyMemoryId> {
    public BuggyMemoryId {
        value = Objects.requireNonNull(value, "value");
    }

    public static BuggyMemoryId random() {
        return new BuggyMemoryId(UUID.randomUUID());
    }

    @Override
    public int compareTo(BuggyMemoryId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
