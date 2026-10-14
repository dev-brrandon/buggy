package de.legitserver.assistant.buggy.nativecore;

import de.legitserver.assistant.core.validation.CoreValidation;

public record BuggySelfCapabilityEntry(
        String capabilityId,
        BuggySelfCapabilityStatus status,
        int confidenceBps,
        String limitation) {

    public BuggySelfCapabilityEntry {
        capabilityId = CoreValidation.requireLowerDottedId(capabilityId, "capabilityId", 96);
        status = CoreValidation.requireNonNull(status, "status");
        if (confidenceBps < 0 || confidenceBps > 10_000) {
            throw new IllegalArgumentException("confidenceBps must be between 0 and 10000");
        }
        limitation = CoreValidation.optionalText(limitation, "limitation", 320);
        if (status == BuggySelfCapabilityStatus.UNAVAILABLE && confidenceBps != 0) {
            throw new IllegalArgumentException("Unavailable capability must have zero confidence");
        }
    }
}
