package de.legitserver.assistant.security.authorization;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.core.assistant.AssistantId;

/** Defines which technical capabilities are enabled for an assistant profile. */
public interface AssistantCapabilityPolicy {
    boolean hasCapability(AssistantId assistantId, AssistantCapabilityId capabilityId);

    /**
     * Descriptor-aware hook used by product profiles. Legacy/static Foundation policies keep their existing behavior.
     */
    default boolean hasCapability(AssistantDescriptor assistant, AssistantCapabilityId capabilityId) {
        if (assistant == null) return false;
        return hasCapability(assistant.assistantId(), capabilityId);
    }
}
