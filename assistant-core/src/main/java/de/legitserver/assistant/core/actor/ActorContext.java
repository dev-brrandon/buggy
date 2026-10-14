package de.legitserver.assistant.core.actor;

import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.UUID;

/** Request-scoped authenticated actor identity without duplicated rank/permission state. */
public record ActorContext(
        ActorId actorId,
        ActorType actorType,
        Origin origin,
        String networkIdentityId,
        UUID minecraftUuid,
        String panelUserId,
        String discordId,
        RequestId requestId,
        TraceId traceId,
        String serverId,
        long securityRevision,
        Instant authenticatedAt) {

    public ActorContext {
        actorId = CoreValidation.requireNonNull(actorId, "actorId");
        actorType = CoreValidation.requireNonNull(actorType, "actorType");
        origin = CoreValidation.requireNonNull(origin, "origin");
        networkIdentityId = CoreValidation.requireSafeToken(networkIdentityId, "networkIdentityId", 128);
        panelUserId = CoreValidation.optionalSafeToken(panelUserId, "panelUserId", 128);
        discordId = CoreValidation.optionalSafeToken(discordId, "discordId", 128);
        requestId = CoreValidation.requireNonNull(requestId, "requestId");
        traceId = CoreValidation.requireNonNull(traceId, "traceId");
        serverId = CoreValidation.optionalSafeToken(serverId, "serverId", 128);
        if (securityRevision < 0) {
            throw new IllegalArgumentException("securityRevision must be >= 0");
        }
        authenticatedAt = CoreValidation.requireNonNull(authenticatedAt, "authenticatedAt");
    }
}
