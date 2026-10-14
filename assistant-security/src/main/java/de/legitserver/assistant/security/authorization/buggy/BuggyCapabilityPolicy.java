package de.legitserver.assistant.security.authorization.buggy;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.security.authorization.AssistantCapabilityPolicy;

/** Canonical Buggy B3 profile binding. NetworkCore remains the permission authority. */
public final class BuggyCapabilityPolicy implements AssistantCapabilityPolicy {
    private final BuggyCapabilityProfile profile;

    public BuggyCapabilityPolicy() {
        this(BuggyCapabilityProfile.canonical());
    }

    BuggyCapabilityPolicy(BuggyCapabilityProfile profile) {
        this.profile = profile;
    }

    @Override
    public boolean hasCapability(AssistantId assistantId, AssistantCapabilityId capabilityId) {
        return assistantId == AssistantId.BUGGY && profile.contains(capabilityId);
    }

    @Override
    public boolean hasCapability(AssistantDescriptor assistant, AssistantCapabilityId capabilityId) {
        if (assistant == null || assistant.assistantId() != AssistantId.BUGGY) return false;
        if (!BuggyCapabilityProfile.PROFILE_ID.equals(assistant.capabilityProfileId())) return false;
        return profile.contains(capabilityId);
    }
}
