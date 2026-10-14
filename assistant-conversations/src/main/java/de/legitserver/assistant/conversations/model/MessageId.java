package de.legitserver.assistant.conversations.model;

import java.util.Objects;
import java.util.UUID;

/** Stable UUID identity of one persisted conversation message. */
public record MessageId(UUID value) implements Comparable<MessageId> {
    public MessageId {
        Objects.requireNonNull(value, "value");
    }

    public static MessageId random() {
        return new MessageId(UUID.randomUUID());
    }

    public static MessageId parse(String value) {
        if (value == null) throw new IllegalArgumentException("messageId must not be null");
        return new MessageId(UUID.fromString(value));
    }

    @Override
    public int compareTo(MessageId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
