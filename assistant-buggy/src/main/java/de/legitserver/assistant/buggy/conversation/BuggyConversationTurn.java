package de.legitserver.assistant.buggy.conversation;

import de.legitserver.assistant.core.validation.CoreValidation;

/** Bounded prior turn supplied to G1.6. It is context only and grants no authority. */
public record BuggyConversationTurn(BuggyConversationRole role, String content) {
    public BuggyConversationTurn {
        role = CoreValidation.requireNonNull(role, "role");
        content = CoreValidation.requireText(content, "content", 12_000);
    }
}
