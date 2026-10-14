package de.legitserver.assistant.buggy.nativecore;

import de.legitserver.assistant.core.validation.CoreValidation;

public record BuggyPersonalityAxisEntry(
        BuggyPersonalityAxis axis,
        int startBps,
        int minimumBps,
        int maximumBps) {

    public BuggyPersonalityAxisEntry {
        axis = CoreValidation.requireNonNull(axis, "axis");
        requireRange(minimumBps, "minimumBps");
        requireRange(startBps, "startBps");
        requireRange(maximumBps, "maximumBps");
        if (minimumBps > startBps || startBps > maximumBps) {
            throw new IllegalArgumentException("Personality axis requires minimum <= start <= maximum");
        }
    }

    private static void requireRange(int value, String field) {
        if (value < 0 || value > 10_000) {
            throw new IllegalArgumentException(field + " must be between 0 and 10000");
        }
    }
}
