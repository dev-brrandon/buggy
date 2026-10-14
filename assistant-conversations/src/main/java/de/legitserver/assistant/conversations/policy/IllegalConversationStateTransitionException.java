package de.legitserver.assistant.conversations.policy;

import de.legitserver.assistant.conversations.model.ConversationState;

/** Domain rejection for an invalid lifecycle transition. */
public final class IllegalConversationStateTransitionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public IllegalConversationStateTransitionException(ConversationState from, ConversationState to) {
        super("Conversation state transition is not allowed: " + from + " -> " + to);
    }
}
