package de.legitserver.assistant.conversations.model;

import java.util.Objects;
import java.util.UUID;

/** Stable UUID identity of one conversation. */
public record ConversationId(UUID value) implements Comparable<ConversationId> {
    public ConversationId {
        Objects.requireNonNull(value, "value");
    }

    public static ConversationId random() {
        return new ConversationId(UUID.randomUUID());
    }

    public static ConversationId parse(String value) {
        if (value == null) throw new IllegalArgumentException("conversationId must not be null");
        return new ConversationId(UUID.fromString(value));
    }

    @Override
    public int compareTo(ConversationId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
