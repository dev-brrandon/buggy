package de.legitserver.assistant.conversations.repository;

import de.legitserver.assistant.conversations.model.Conversation;
import de.legitserver.assistant.conversations.model.ConversationMessage;
import java.util.Objects;

/** Atomic append result containing the new message and incremented conversation head. */
public record AppendMessageResult(Conversation conversation, ConversationMessage message) {
    public AppendMessageResult {
        Objects.requireNonNull(conversation, "conversation");
        Objects.requireNonNull(message, "message");
    }
}
