package de.legitserver.assistant.conversations.model;

/** Persistent conversation lifecycle state. */
public enum ConversationState {
    CREATED(false),
    ACTIVE(false),
    WAITING_FOR_INPUT(false),
    WAITING_FOR_APPROVAL(false),
    PROCESSING(false),
    COMPLETED(true),
    CANCELLED(true),
    EXPIRED(true),
    FAILED(true);

    private final boolean terminal;

    ConversationState(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
