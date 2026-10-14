package de.legitserver.assistant.buggy.conversation;

import de.legitserver.assistant.core.validation.CoreValidation;

public record BuggyConversationEntity(
        BuggyConversationEntityType type,
        String value,
        int confidenceBps,
        boolean contextual) {

    public BuggyConversationEntity {
        type = CoreValidation.requireNonNull(type, "type");
        value = CoreValidation.requireText(value, "value", 256);
        if (confidenceBps < 0 || confidenceBps > 10_000) {
            throw new IllegalArgumentException("confidenceBps must be between 0 and 10000");
        }
    }
}
