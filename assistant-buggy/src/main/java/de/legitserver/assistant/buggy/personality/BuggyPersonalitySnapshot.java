package de.legitserver.assistant.buggy.personality;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;

/** Active Personality projection: immutable seed plus the current append-only state revision. */
public record BuggyPersonalitySnapshot(
        BuggyPersonalitySeed seed,
        BuggyPersonalityRevision current,
        Instant updatedAt) {

    public BuggyPersonalitySnapshot {
        seed = CoreValidation.requireNonNull(seed, "seed");
        current = CoreValidation.requireNonNull(current, "current");
        updatedAt = CoreValidation.requireNonNull(updatedAt, "updatedAt");
    }
}
