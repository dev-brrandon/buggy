package de.legitserver.assistant.security.authorization;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.assistant.AssistantCapabilityId;

/** Evaluates resource-level visibility/management rules after actor permissions pass. */
public interface ResourceAuthorizationPort {
    ResourceAuthorizationStatus authorize(
            ActorContext actor,
            AuthoritativeSecuritySnapshot snapshot,
            AssistantCapabilityId capabilityId,
            ResourceRef resource);
}
