package de.legitserver.assistant.pipeline.policy;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.assistant.AssistantId;
import java.util.Objects;

/** Server-derived rate-limit bucket key. */
public record RateLimitKey(ActorId actorId, AssistantId assistantId, AssistantCapabilityId capabilityId) {
    public RateLimitKey {
        actorId = Objects.requireNonNull(actorId, "actorId");
        assistantId = Objects.requireNonNull(assistantId, "assistantId");
        capabilityId = Objects.requireNonNull(capabilityId, "capabilityId");
    }
}
