package de.legitserver.assistant.conversations.repository;

/** Raised when persisted message content would exceed the conversation context byte budget. */
public final class ConversationContextLimitExceededException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ConversationContextLimitExceededException(int maxUtf8Bytes) {
        super("Conversation context byte limit exceeded: " + maxUtf8Bytes);
    }
}
