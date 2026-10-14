package de.legitserver.assistant.buggy.exclusive;

import de.legitserver.assistant.core.validation.CoreValidation;

public record BuggyHistoricalDelta(String attribute, BuggyHistoricalDeltaType type, String before, String after) {
    public BuggyHistoricalDelta {
        attribute = CoreValidation.requireSafeToken(attribute, "attribute", 128);
        type = CoreValidation.requireNonNull(type, "type");
        before = before == null ? null : CoreValidation.requireText(before, "before", 2048);
        after = after == null ? null : CoreValidation.requireText(after, "after", 2048);
    }
}
