package de.legitserver.assistant.buggy.personality;

import de.legitserver.assistant.core.validation.CoreValidation;

/** One effective entry in a complete, versioned Personality State snapshot. */
public record BuggyPersonalityStateEntry(
        BuggyPersonalityDomain domain,
        String attributeKey,
        String value,
        int confidenceBps,
        String sourceObservationId) {

    public BuggyPersonalityStateEntry {
        domain = CoreValidation.requireNonNull(domain, "domain");
        attributeKey = BuggyPersonalityBoundaryGuard.requireSafeAttributeKey(attributeKey);
        value = CoreValidation.requireText(value, "value", 512);
        if (confidenceBps < 0 || confidenceBps > 10_000) {
            throw new IllegalArgumentException("confidenceBps must be between 0 and 10000");
        }
        sourceObservationId = CoreValidation.optionalSafeToken(sourceObservationId, "sourceObservationId", 64);
        BuggyPersonalityBoundaryGuard.requireNoAuthorityMutation(domain, attributeKey, value);
    }

    public String identityKey() {
        return domain.name() + ":" + attributeKey;
    }
}
