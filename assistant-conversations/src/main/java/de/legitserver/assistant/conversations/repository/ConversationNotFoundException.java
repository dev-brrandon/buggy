package de.legitserver.assistant.conversations.repository;

import de.legitserver.assistant.conversations.model.ConversationId;

/** Raised when a referenced conversation no longer exists. */
public final class ConversationNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ConversationNotFoundException(ConversationId id) {
        super("Conversation not found: " + id);
    }
}
