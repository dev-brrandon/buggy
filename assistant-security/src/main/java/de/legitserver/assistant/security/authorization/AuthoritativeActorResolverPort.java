package de.legitserver.assistant.security.authorization;

import de.legitserver.assistant.core.actor.ActorContext;

/** Resolves a request actor against the authoritative NetworkCore/identity plane. */
public interface AuthoritativeActorResolverPort {
    ActorResolution resolve(ActorContext presentedActor);
}
