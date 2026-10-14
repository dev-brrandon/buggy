package de.legitserver.assistant.integrations.networkcore;

import de.legitserver.assistant.core.actor.ActorContext;
import java.util.UUID;

/** Client boundary for the private NetworkCore authority transport. */
public interface NetworkCoreAuthorityClient {
    NetworkCoreActorLookup resolveActor(ActorContext presentedActor);

    NetworkCoreSecurityLookup securitySnapshot(UUID actorUuid, long minimumRevision);
}
