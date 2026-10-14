package de.legitserver.assistant.buggy.selfdev;

import java.util.Objects;
import java.util.UUID;

/** Stable identity for one Buggy Self-Development proposal lifecycle. */
public record BuggySelfDevelopmentId(UUID value) implements Comparable<BuggySelfDevelopmentId> {
    public BuggySelfDevelopmentId {
        value = Objects.requireNonNull(value, "value");
    }

    public static BuggySelfDevelopmentId random() {
        return new BuggySelfDevelopmentId(UUID.randomUUID());
    }

    @Override
    public int compareTo(BuggySelfDevelopmentId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
