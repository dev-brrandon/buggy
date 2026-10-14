package de.legitserver.assistant.conversations.model;

import de.legitserver.assistant.conversations.policy.ConversationLimits;
import de.legitserver.assistant.conversations.validation.ConversationValidation;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.classification.DataClassification;
import java.util.Map;
import java.util.Objects;

/** Validated message payload before persistence assigns id, sequence and timestamp. */
public record NewConversationMessage(
        MessageAuthorType authorType,
        ActorId actorId,
        String content,
        DataClassification classification,
        MessageRedactionState redactionState,
        Map<String, String> metadata) {

    public NewConversationMessage {
        Objects.requireNonNull(authorType, "authorType");
        if (authorType == MessageAuthorType.HUMAN && actorId == null) {
            throw new IllegalArgumentException("HUMAN message requires actorId");
        }
        content = ConversationValidation.requireContent(content, ConversationLimits.ABSOLUTE_MAX_MESSAGE_BYTES);
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(redactionState, "redactionState");
        metadata = ConversationValidation.boundedMetadata(
                metadata,
                ConversationLimits.ABSOLUTE_MAX_METADATA_ENTRIES,
                ConversationLimits.ABSOLUTE_MAX_METADATA_BYTES);
    }
}
