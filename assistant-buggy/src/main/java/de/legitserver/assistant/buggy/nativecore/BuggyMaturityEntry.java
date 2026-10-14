package de.legitserver.assistant.buggy.nativecore;

import de.legitserver.assistant.core.validation.CoreValidation;

public record BuggyMaturityEntry(BuggyMaturityDimension dimension, int scoreBps) {
    public BuggyMaturityEntry {
        dimension = CoreValidation.requireNonNull(dimension, "dimension");
        if (scoreBps < 0 || scoreBps > 10_000) {
            throw new IllegalArgumentException("scoreBps must be between 0 and 10000");
        }
    }
}
