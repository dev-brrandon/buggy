package de.legitserver.assistant.buggy.activity;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Owner-facing compact representation used to avoid noisy low-value activity streams. */
public record BuggyActivityBundle(
        BuggyAttentionLevel attention,
        int count,
        Instant firstAt,
        Instant lastAt,
        List<BuggyActivityEntry> entries) {
    public BuggyActivityBundle {
        attention = Objects.requireNonNull(attention, "attention");
        if (count < 1) throw new IllegalArgumentException("count must be positive");
        firstAt = Objects.requireNonNull(firstAt, "firstAt");
        lastAt = Objects.requireNonNull(lastAt, "lastAt");
        if (lastAt.isBefore(firstAt)) throw new IllegalArgumentException("lastAt precedes firstAt");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (entries.size() != count) throw new IllegalArgumentException("count/entries mismatch");
    }
}
