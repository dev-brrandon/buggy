package de.legitserver.assistant.buggy.selfdev;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.runtime.model.ToolId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Append-only evidence pointer. Artifact/source bytes are never persisted here. */
public record BuggySelfDevelopmentEvidence(
        UUID evidenceId,
        BuggySelfDevelopmentId itemId,
        long itemRevision,
        BuggySelfDevelopmentEvidenceType type,
        ToolId toolId,
        String referenceId,
        String sha256,
        boolean successful,
        ActorId observedBy,
        Instant observedAt) {
    public BuggySelfDevelopmentEvidence {
        evidenceId = Objects.requireNonNull(evidenceId, "evidenceId");
        itemId = Objects.requireNonNull(itemId, "itemId");
        if (itemRevision < 1) throw new IllegalArgumentException("itemRevision must be >= 1");
        type = Objects.requireNonNull(type, "type");
        referenceId = CoreValidation.requireSafeToken(referenceId, "referenceId", 512);
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be 64 lowercase hex characters");
        }
        observedBy = Objects.requireNonNull(observedBy, "observedBy");
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
        if (toolId == null && type != BuggySelfDevelopmentEvidenceType.SELF_REVIEW
                && type != BuggySelfDevelopmentEvidenceType.APPROVAL_REQUEST
                && type != BuggySelfDevelopmentEvidenceType.DEPLOYMENT
                && type != BuggySelfDevelopmentEvidenceType.ROLLBACK) {
            throw new IllegalArgumentException("toolId is required for executable evidence type " + type);
        }
    }
}
