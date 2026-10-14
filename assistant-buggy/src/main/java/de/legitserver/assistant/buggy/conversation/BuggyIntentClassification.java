package de.legitserver.assistant.buggy.conversation;

import de.legitserver.assistant.core.validation.CoreValidation;

public record BuggyIntentClassification(
        BuggyConversationIntent intent,
        int confidenceBps,
        String evidence) {

    public BuggyIntentClassification {
        intent = CoreValidation.requireNonNull(intent, "intent");
        if (confidenceBps < 0 || confidenceBps > 10_000) {
            throw new IllegalArgumentException("confidenceBps must be between 0 and 10000");
        }
        evidence = CoreValidation.requireText(evidence, "evidence", 256);
    }
}
