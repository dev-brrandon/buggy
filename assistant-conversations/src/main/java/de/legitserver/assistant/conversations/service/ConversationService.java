package de.legitserver.assistant.conversations.service;

import de.legitserver.assistant.conversations.model.Conversation;
import de.legitserver.assistant.conversations.model.ConversationContextType;
import de.legitserver.assistant.conversations.model.ConversationId;
import de.legitserver.assistant.conversations.model.ConversationState;
import de.legitserver.assistant.conversations.model.NewConversationMessage;
import de.legitserver.assistant.conversations.policy.ConversationExpiryPolicy;
import de.legitserver.assistant.conversations.policy.ConversationLimits;
import de.legitserver.assistant.conversations.policy.ConversationStateMachine;
import de.legitserver.assistant.conversations.repository.AppendMessageResult;
import de.legitserver.assistant.conversations.repository.ConversationNotFoundException;
import de.legitserver.assistant.conversations.repository.ConversationRepository;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Transaction-agnostic application service for conversation lifecycle operations. */
public final class ConversationService {
    private final ConversationRepository repository;
    private final ConversationStateMachine stateMachine;
    private final ConversationExpiryPolicy expiryPolicy;
    private final ConversationLimits limits;
    private final Clock clock;

    public ConversationService(
            ConversationRepository repository,
            ConversationStateMachine stateMachine,
            ConversationExpiryPolicy expiryPolicy,
            ConversationLimits limits,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine");
        this.expiryPolicy = Objects.requireNonNull(expiryPolicy, "expiryPolicy");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Conversation create(
            AssistantId assistantId,
            ActorId ownerActorId,
            Origin origin,
            ConversationContextType contextType,
            DataClassification classification,
            Map<String, String> metadata) {
        Instant now = clock.instant();
        Conversation conversation = new Conversation(
                ConversationId.random(),
                assistantId,
                ownerActorId,
                origin,
                contextType,
                ConversationState.CREATED,
                classification,
                now,
                now,
                now,
                expiryPolicy.expiresAt(contextType, now),
                0,
                metadata);
        repository.create(conversation);
        return conversation;
    }

    public Conversation transition(ConversationId id, long expectedRevision, ConversationState targetState) {
        Conversation current = repository.findById(id).orElseThrow(() -> new ConversationNotFoundException(id));
        stateMachine.requireTransition(current.state(), targetState);
        Instant now = clock.instant();
        Instant expiresAt = targetState.isTerminal() ? null : expiryPolicy.expiresAt(current.contextType(), now);
        return repository.transition(id, expectedRevision, targetState, now, expiresAt);
    }

    public AppendMessageResult append(ConversationId id, long expectedRevision, NewConversationMessage message) {
        Conversation current = repository.findById(id).orElseThrow(() -> new ConversationNotFoundException(id));
        if (current.state().isTerminal()) {
            throw new IllegalStateException("Cannot append to terminal conversation: " + current.state());
        }
        Instant now = clock.instant();
        return repository.appendMessage(
                id,
                expectedRevision,
                message,
                now,
                expiryPolicy.expiresAt(current.contextType(), now),
                limits.maxMessages(),
                limits.maxConversationContextUtf8Bytes());
    }
}
