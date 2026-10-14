package de.legitserver.assistant.buggy.exclusive;

import de.legitserver.assistant.core.validation.CoreValidation;

public record BuggyArchitectureFinding(
        BuggyArchitectureFindingType type,
        String decisionId,
        String property,
        String expected,
        String actual,
        String rationale) {
    public BuggyArchitectureFinding {
        type = CoreValidation.requireNonNull(type, "type");
        decisionId = CoreValidation.requireSafeToken(decisionId, "decisionId", 160);
        property = CoreValidation.requireSafeToken(property, "property", 128);
        expected = CoreValidation.requireText(expected, "expected", 1024);
        actual = actual == null ? "<missing>" : CoreValidation.requireText(actual, "actual", 1024);
        rationale = CoreValidation.requireText(rationale, "rationale", 2048);
    }
}
