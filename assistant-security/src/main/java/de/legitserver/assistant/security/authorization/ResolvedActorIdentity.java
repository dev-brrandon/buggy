package de.legitserver.assistant.security.authorization;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.UUID;

/** Canonical actor binding returned by the authority. */
public record ResolvedActorIdentity(
        ActorId actorId,
        String networkIdentityId,
        UUID minecraftUuid,
        long authorizationRevision) {

    public ResolvedActorIdentity {
        actorId = CoreValidation.requireNonNull(actorId, "actorId");
        networkIdentityId = CoreValidation.requireSafeToken(networkIdentityId, "networkIdentityId", 128);
        minecraftUuid = CoreValidation.requireNonNull(minecraftUuid, "minecraftUuid");
        if (authorizationRevision < 0) {
            throw new IllegalArgumentException("authorizationRevision must be >= 0");
        }
    }
}
