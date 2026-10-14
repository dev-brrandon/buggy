package de.legitserver.assistant.security.authorization;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Immutable policy useful until persistent assistant profiles arrive in later phases. */
public final class StaticAssistantCapabilityPolicy implements AssistantCapabilityPolicy {
    private final Map<AssistantId, Set<AssistantCapabilityId>> capabilities;

    public StaticAssistantCapabilityPolicy(Map<AssistantId, Set<AssistantCapabilityId>> capabilities) {
        CoreValidation.requireNonNull(capabilities, "capabilities");
        EnumMap<AssistantId, Set<AssistantCapabilityId>> copy = new EnumMap<>(AssistantId.class);
        capabilities.forEach((assistant, values) ->
                copy.put(CoreValidation.requireNonNull(assistant, "assistant"), values == null ? Set.of() : Set.copyOf(values)));
        this.capabilities = Map.copyOf(copy);
    }

    @Override
    public boolean hasCapability(AssistantId assistantId, AssistantCapabilityId capabilityId) {
        CoreValidation.requireNonNull(assistantId, "assistantId");
        CoreValidation.requireNonNull(capabilityId, "capabilityId");
        return capabilities.getOrDefault(assistantId, Set.of()).contains(capabilityId);
    }
}
