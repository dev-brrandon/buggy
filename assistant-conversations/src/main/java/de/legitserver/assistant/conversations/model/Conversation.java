package de.legitserver.assistant.conversations.model;

import de.legitserver.assistant.conversations.policy.ConversationLimits;
import de.legitserver.assistant.conversations.validation.ConversationValidation;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Immutable persisted conversation aggregate head. */
public record Conversation(
        ConversationId conversationId,
        AssistantId assistantId,
        ActorId ownerActorId,
        Origin origin,
        ConversationContextType contextType,
        ConversationState state,
        DataClassification classification,
        Instant createdAt,
        Instant updatedAt,
        Instant lastActivityAt,
        Instant expiresAt,
        long revision,
        Map<String, String> metadata) {

    public Conversation {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(assistantId, "assistantId");
        Objects.requireNonNull(ownerActorId, "ownerActorId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(contextType, "contextType");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(lastActivityAt, "lastActivityAt");
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt before createdAt");
        if (lastActivityAt.isBefore(createdAt)) throw new IllegalArgumentException("lastActivityAt before createdAt");
        if (expiresAt != null && expiresAt.isBefore(lastActivityAt)) {
            throw new IllegalArgumentException("expiresAt before lastActivityAt");
        }
        metadata = ConversationValidation.boundedMetadata(
                metadata,
                ConversationLimits.ABSOLUTE_MAX_METADATA_ENTRIES,
                ConversationLimits.ABSOLUTE_MAX_METADATA_BYTES);
    }
}
