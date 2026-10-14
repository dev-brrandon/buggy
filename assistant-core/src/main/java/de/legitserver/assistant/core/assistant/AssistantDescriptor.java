package de.legitserver.assistant.core.assistant;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;

/** Immutable technical descriptor for a registered assistant identity. */
public record AssistantDescriptor(
        AssistantId assistantId,
        String displayName,
        boolean enabled,
        String capabilityProfileId,
        String policyProfileId,
        String knowledgeProfileId,
        String runtimeProfileId,
        String personalityProfileId,
        long revision,
        Instant updatedAt) {

    public AssistantDescriptor {
        assistantId = CoreValidation.requireNonNull(assistantId, "assistantId");
        displayName = CoreValidation.requireText(displayName, "displayName", 80);
        capabilityProfileId = CoreValidation.requireSafeToken(capabilityProfileId, "capabilityProfileId", 96);
        policyProfileId = CoreValidation.requireSafeToken(policyProfileId, "policyProfileId", 96);
        knowledgeProfileId = CoreValidation.requireSafeToken(knowledgeProfileId, "knowledgeProfileId", 96);
        runtimeProfileId = CoreValidation.requireSafeToken(runtimeProfileId, "runtimeProfileId", 96);
        personalityProfileId = CoreValidation.optionalSafeToken(personalityProfileId, "personalityProfileId", 96);
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be >= 0");
        }
        updatedAt = CoreValidation.requireNonNull(updatedAt, "updatedAt");
    }
}
