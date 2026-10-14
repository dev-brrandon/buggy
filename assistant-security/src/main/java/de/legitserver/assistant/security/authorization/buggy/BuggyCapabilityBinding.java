package de.legitserver.assistant.security.authorization.buggy;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.validation.CoreValidation;

/** Product-level mapping of a canonical Buggy capability to its intended authority verb. */
public record BuggyCapabilityBinding(
        BuggyCapabilityGroup group,
        AssistantCapabilityId capabilityId,
        BuggyAuthorityMode authorityMode) {

    public BuggyCapabilityBinding {
        group = CoreValidation.requireNonNull(group, "group");
        capabilityId = CoreValidation.requireNonNull(capabilityId, "capabilityId");
        authorityMode = CoreValidation.requireNonNull(authorityMode, "authorityMode");
        if (!capabilityId.value().startsWith("assistant.buggy.")) {
            throw new IllegalArgumentException("Buggy capability binding must use assistant.buggy.* namespace");
        }
        if (authorityMode == BuggyAuthorityMode.OWNER_ONLY) {
            throw new IllegalArgumentException("OWNER_ONLY actions must not be granted as Buggy capabilities");
        }
    }
}
