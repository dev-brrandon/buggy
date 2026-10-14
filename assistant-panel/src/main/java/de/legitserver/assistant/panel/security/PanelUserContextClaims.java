package de.legitserver.assistant.panel.security;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;

/**
 * Signed identity binding emitted only by the trusted Panel Backend.
 * It deliberately contains no rank, permission, capability or policy state.
 */
public record PanelUserContextClaims(
        ActorId actorId,
        String networkIdentityId,
        String panelUserId,
        RequestId requestId,
        TraceId traceId,
        String serviceCredentialId,
        String serverId,
        Instant issuedAt,
        Instant expiresAt,
        String jti) {
    public PanelUserContextClaims {
        actorId = CoreValidation.requireNonNull(actorId, "actorId");
        networkIdentityId = CoreValidation.requireSafeToken(networkIdentityId, "networkIdentityId", 128);
        panelUserId = CoreValidation.requireSafeToken(panelUserId, "panelUserId", 128);
        requestId = CoreValidation.requireNonNull(requestId, "requestId");
        traceId = CoreValidation.requireNonNull(traceId, "traceId");
        serviceCredentialId = CoreValidation.requireSafeToken(serviceCredentialId, "serviceCredentialId", 128);
        serverId = CoreValidation.optionalSafeToken(serverId, "serverId", 128);
        issuedAt = CoreValidation.requireNonNull(issuedAt, "issuedAt");
        expiresAt = CoreValidation.requireNonNull(expiresAt, "expiresAt");
        jti = CoreValidation.requireSafeToken(jti, "jti", 128);
        if (!expiresAt.isAfter(issuedAt)) throw new IllegalArgumentException("expiresAt must be after issuedAt");
    }
}
