package de.legitserver.assistant.conversations.repository;

import de.legitserver.assistant.conversations.model.Conversation;
import de.legitserver.assistant.conversations.model.ConversationId;
import de.legitserver.assistant.conversations.model.ConversationMessage;
import de.legitserver.assistant.conversations.model.ConversationState;
import de.legitserver.assistant.conversations.model.NewConversationMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence port owned by the Conversation domain. */
public interface ConversationRepository {
    void create(Conversation conversation);

    Optional<Conversation> findById(ConversationId conversationId);

    List<ConversationMessage> listMessages(ConversationId conversationId, long afterSequenceExclusive, int limit);

    long countMessages(ConversationId conversationId);

    Conversation transition(
            ConversationId conversationId,
            long expectedRevision,
            ConversationState targetState,
            Instant now,
            Instant expiresAt);

    AppendMessageResult appendMessage(
            ConversationId conversationId,
            long expectedRevision,
            NewConversationMessage message,
            Instant now,
            Instant expiresAt,
            int maxMessages,
            int maxConversationContextUtf8Bytes);

    List<Conversation> findExpiredCandidates(Instant now, int limit);
}
