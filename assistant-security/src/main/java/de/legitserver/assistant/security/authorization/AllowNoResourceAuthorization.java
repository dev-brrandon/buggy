package de.legitserver.assistant.security.authorization;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.assistant.AssistantCapabilityId;

/** Allows only requests that do not target a resource. Useful as a secure default. */
public final class AllowNoResourceAuthorization implements ResourceAuthorizationPort {
    @Override
    public ResourceAuthorizationStatus authorize(
            ActorContext actor,
            AuthoritativeSecuritySnapshot snapshot,
            AssistantCapabilityId capabilityId,
            ResourceRef resource) {
        return resource == null ? ResourceAuthorizationStatus.ALLOW : ResourceAuthorizationStatus.DENY;
    }
}
