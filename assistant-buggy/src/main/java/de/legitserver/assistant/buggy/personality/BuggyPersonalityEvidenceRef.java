package de.legitserver.assistant.buggy.personality;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;

/** Reference-only evidence. It never stores message bodies, credentials or other raw source payloads. */
public record BuggyPersonalityEvidenceRef(
        BuggyPersonalityEvidenceType type,
        String referenceId,
        Instant observedAt) {

    public BuggyPersonalityEvidenceRef {
        type = CoreValidation.requireNonNull(type, "type");
        referenceId = CoreValidation.requireSafeToken(referenceId, "referenceId", 160);
        observedAt = CoreValidation.requireNonNull(observedAt, "observedAt");
    }
}
