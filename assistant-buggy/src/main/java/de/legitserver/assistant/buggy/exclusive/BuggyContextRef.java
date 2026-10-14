package de.legitserver.assistant.buggy.exclusive;

import de.legitserver.assistant.core.validation.CoreValidation;

/** Safe reference to an already-authoritative external/domain context. */
public record BuggyContextRef(BuggyContextType type, String contextId, String displayLabel) {
    public BuggyContextRef {
        type = CoreValidation.requireNonNull(type, "type");
        contextId = CoreValidation.requireSafeToken(contextId, "contextId", 160);
        displayLabel = CoreValidation.requireText(displayLabel, "displayLabel", 256);
    }
}
