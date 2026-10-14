package de.legitserver.assistant.buggy.personality;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.List;

/** Append-only behavioral observation that may later be promoted into Personality State. */
public record BuggyPersonalityObservation(
        String observationId,
        BuggyPersonalityDomain domain,
        String attributeKey,
        String observedValue,
        BuggyPersonalityObservationOrigin origin,
        int confidenceBps,
        List<BuggyPersonalityEvidenceRef> evidence,
        String note,
        ActorId createdBy,
        Instant createdAt,
        String sha256) {

    public BuggyPersonalityObservation {
        observationId = CoreValidation.requireSafeToken(observationId, "observationId", 64);
        domain = CoreValidation.requireNonNull(domain, "domain");
        attributeKey = BuggyPersonalityBoundaryGuard.requireSafeAttributeKey(attributeKey);
        observedValue = CoreValidation.requireText(observedValue, "observedValue", 512);
        origin = CoreValidation.requireNonNull(origin, "origin");
        if (confidenceBps < 1 || confidenceBps > 10_000) {
            throw new IllegalArgumentException("confidenceBps must be between 1 and 10000");
        }
        evidence = List.copyOf(CoreValidation.requireNonNull(evidence, "evidence"));
        if (evidence.isEmpty()) throw new IllegalArgumentException("Personality observation requires evidence references");
        note = CoreValidation.requireText(note, "note", 512);
        createdBy = CoreValidation.requireNonNull(createdBy, "createdBy");
        createdAt = CoreValidation.requireNonNull(createdAt, "createdAt");
        sha256 = CoreValidation.requireSafeToken(sha256, "sha256", 64);
        BuggyPersonalityBoundaryGuard.requireNoAuthorityMutation(domain, attributeKey, observedValue);
        String expected = BuggyPersonalityCanonicalizer.observationSha256(
                observationId,
                domain,
                attributeKey,
                observedValue,
                origin,
                confidenceBps,
                evidence,
                note,
                createdBy,
                createdAt);
        if (!expected.equals(sha256)) throw new IllegalArgumentException("Buggy personality observation SHA-256 mismatch");
    }

    public BuggyPersonalityStateEntry toStateEntry() {
        return new BuggyPersonalityStateEntry(domain, attributeKey, observedValue, confidenceBps, observationId);
    }
}
