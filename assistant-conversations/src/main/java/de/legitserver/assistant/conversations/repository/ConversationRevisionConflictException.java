package de.legitserver.assistant.conversations.repository;

import de.legitserver.assistant.conversations.model.ConversationId;

/** Domain-level optimistic concurrency conflict without infrastructure coupling. */
public final class ConversationRevisionConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ConversationRevisionConflictException(ConversationId id, long expectedRevision) {
        super("Conversation revision conflict: " + id + " expected=" + expectedRevision);
    }
}
