package de.legitserver.assistant.buggy.development;

import de.legitserver.assistant.buggy.nativecore.BuggySelfCapabilityStatus;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;

/** Append-only current-self revision layered above the immutable G1.4 birth capability baseline. */
public record BuggySelfCapabilityRevision(
        String capabilityId,
        long revision,
        Long previousRevision,
        BuggySelfCapabilityStatus status,
        int confidenceBps,
        String limitation,
        String evidenceRef,
        ActorId changedBy,
        Instant changedAt) {

    public BuggySelfCapabilityRevision {
        capabilityId = CoreValidation.requireLowerDottedId(capabilityId, "capabilityId", 96);
        if (revision < 1) throw new IllegalArgumentException("revision must be >= 1");
        if ((revision == 1 && previousRevision != null)
                || (revision > 1 && (previousRevision == null || previousRevision != revision - 1))) {
            throw new IllegalArgumentException("Invalid capability revision chain");
        }
        status = CoreValidation.requireNonNull(status, "status");
        if (confidenceBps < 0 || confidenceBps > 10_000) {
            throw new IllegalArgumentException("confidenceBps must be between 0 and 10000");
        }
        limitation = CoreValidation.optionalText(limitation, "limitation", 320);
        evidenceRef = CoreValidation.requireText(evidenceRef, "evidenceRef", 512);
        changedBy = CoreValidation.requireNonNull(changedBy, "changedBy");
        changedAt = CoreValidation.requireNonNull(changedAt, "changedAt");
        if (status == BuggySelfCapabilityStatus.UNAVAILABLE && confidenceBps != 0) {
            throw new IllegalArgumentException("Unavailable capability must have zero confidence");
        }
    }
}
