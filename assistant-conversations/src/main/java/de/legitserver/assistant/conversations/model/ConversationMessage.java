package de.legitserver.assistant.conversations.model;

import de.legitserver.assistant.conversations.policy.ConversationLimits;
import de.legitserver.assistant.conversations.validation.ConversationValidation;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.classification.DataClassification;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Immutable persisted conversation message. */
public record ConversationMessage(
        MessageId messageId,
        ConversationId conversationId,
        long sequenceNo,
        MessageAuthorType authorType,
        ActorId actorId,
        String content,
        String contentSha256,
        DataClassification classification,
        MessageRedactionState redactionState,
        Instant createdAt,
        long revision,
        Map<String, String> metadata) {

    public ConversationMessage {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(conversationId, "conversationId");
        if (sequenceNo < 1) throw new IllegalArgumentException("sequenceNo must be positive");
        Objects.requireNonNull(authorType, "authorType");
        if (authorType == MessageAuthorType.HUMAN && actorId == null) {
            throw new IllegalArgumentException("HUMAN message requires actorId");
        }
        content = ConversationValidation.requireContent(content, ConversationLimits.ABSOLUTE_MAX_MESSAGE_BYTES);
        contentSha256 = ConversationValidation.requireSha256(contentSha256);
        if (!ConversationValidation.sha256(content).equals(contentSha256)) {
            throw new IllegalArgumentException("contentSha256 does not match content");
        }
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(redactionState, "redactionState");
        Objects.requireNonNull(createdAt, "createdAt");
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
        metadata = ConversationValidation.boundedMetadata(
                metadata,
                ConversationLimits.ABSOLUTE_MAX_METADATA_ENTRIES,
                ConversationLimits.ABSOLUTE_MAX_METADATA_BYTES);
    }
}
