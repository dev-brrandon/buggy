package de.legitserver.assistant.pipeline.conversation;

import de.legitserver.assistant.conversations.model.Conversation;
import de.legitserver.assistant.conversations.model.ConversationId;
import de.legitserver.assistant.conversations.repository.ConversationRepository;
import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.pipeline.model.PipelineRequest;
import de.legitserver.assistant.pipeline.port.PipelineConversationResolution;
import de.legitserver.assistant.pipeline.port.PipelineConversationResolverPort;
import java.time.Instant;

/** Resolves and ownership-checks an optional conversation without mutating it. */
public final class RepositoryConversationResolver implements PipelineConversationResolverPort {
    private final ConversationRepository repository;

    public RepositoryConversationResolver(ConversationRepository repository) {
        if (repository == null) throw new IllegalArgumentException("repository must not be null");
        this.repository = repository;
    }

    @Override
    public PipelineConversationResolution resolve(PipelineRequest request, ActorContext actor) {
        String raw = request.context().conversationId();
        if (raw == null) return PipelineConversationResolution.none();
        ConversationId id;
        try {
            id = ConversationId.parse(raw);
        } catch (IllegalArgumentException invalid) {
            return PipelineConversationResolution.denied("CONVERSATION_ID_INVALID");
        }
        Conversation conversation;
        try {
            conversation = repository.findById(id).orElse(null);
        } catch (RuntimeException unavailable) {
            return PipelineConversationResolution.denied("CONVERSATION_STORE_UNAVAILABLE");
        }
        if (conversation == null) return PipelineConversationResolution.denied("CONVERSATION_NOT_FOUND");
        if (!conversation.ownerActorId().equals(actor.actorId())
                || conversation.assistantId() != request.assistant().assistantId()
                || conversation.origin() != actor.origin()) {
            return PipelineConversationResolution.denied("CONVERSATION_ACCESS_DENIED");
        }
        if (conversation.state().isTerminal()) return PipelineConversationResolution.denied("CONVERSATION_TERMINAL");
        Instant expiresAt = conversation.expiresAt();
        if (expiresAt != null && !expiresAt.isAfter(request.context().receivedAt())) {
            return PipelineConversationResolution.denied("CONVERSATION_EXPIRED");
        }
        return PipelineConversationResolution.found(conversation);
    }
}
