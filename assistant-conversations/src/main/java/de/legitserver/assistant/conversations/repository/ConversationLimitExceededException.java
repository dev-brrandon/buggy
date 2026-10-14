package de.legitserver.assistant.conversations.repository;

/** Raised when a hard per-conversation message bound would be exceeded. */
public final class ConversationLimitExceededException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ConversationLimitExceededException(int maxMessages) {
        super("Conversation message limit exceeded: " + maxMessages);
    }
}
