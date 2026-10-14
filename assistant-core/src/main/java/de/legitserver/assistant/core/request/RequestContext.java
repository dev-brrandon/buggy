package de.legitserver.assistant.core.request;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;

/** Immutable correlation context carried through application boundaries. */
public record RequestContext(
        RequestId requestId,
        TraceId traceId,
        ActorId actorId,
        Origin origin,
        AssistantId assistantId,
        String conversationId,
        String serverId,
        Instant receivedAt) {

    public RequestContext {
        requestId = CoreValidation.requireNonNull(requestId, "requestId");
        traceId = CoreValidation.requireNonNull(traceId, "traceId");
        actorId = CoreValidation.requireNonNull(actorId, "actorId");
        origin = CoreValidation.requireNonNull(origin, "origin");
        conversationId = CoreValidation.optionalSafeToken(conversationId, "conversationId", 128);
        serverId = CoreValidation.optionalSafeToken(serverId, "serverId", 128);
        receivedAt = CoreValidation.requireNonNull(receivedAt, "receivedAt");
    }
}
