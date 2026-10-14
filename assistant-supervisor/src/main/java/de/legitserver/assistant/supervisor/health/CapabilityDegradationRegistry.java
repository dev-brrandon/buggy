package de.legitserver.assistant.supervisor.health;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.assistant.AssistantId;
import java.util.EnumMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Operational degradation projection only; this registry is never an authorization authority. */
public final class CapabilityDegradationRegistry {
    private final EnumMap<AssistantId, Set<AssistantCapabilityId>> degraded = new EnumMap<>(AssistantId.class);

    public CapabilityDegradationRegistry() {
        for (AssistantId assistantId : AssistantId.values()) degraded.put(assistantId, ConcurrentHashMap.newKeySet());
    }

    public void markDegraded(AssistantId assistantId, AssistantCapabilityId capability) {
        require(assistantId, capability);
        degraded.get(assistantId).add(capability);
    }

    public void markHealthy(AssistantId assistantId, AssistantCapabilityId capability) {
        require(assistantId, capability);
        degraded.get(assistantId).remove(capability);
    }

    public Set<AssistantCapabilityId> snapshot(AssistantId assistantId) {
        if (assistantId == null) throw new IllegalArgumentException("assistantId must not be null");
        return Set.copyOf(degraded.get(assistantId));
    }

    private static void require(AssistantId assistantId, AssistantCapabilityId capability) {
        if (assistantId == null || capability == null) throw new IllegalArgumentException("degradation fields must not be null");
    }
}
