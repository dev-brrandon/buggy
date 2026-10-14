package de.legitserver.assistant.buggy.personality;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.util.List;

/** Input model for a new personality observation. Free text is sanitized by BuggyPersonalityContentPolicy. */
public record BuggyPersonalityObservationDraft(
        BuggyPersonalityDomain domain,
        String attributeKey,
        String observedValue,
        BuggyPersonalityObservationOrigin origin,
        int confidenceBps,
        List<BuggyPersonalityEvidenceRef> evidence,
        String note,
        ActorId createdBy) {

    public BuggyPersonalityObservationDraft {
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
        BuggyPersonalityBoundaryGuard.requireNoAuthorityMutation(domain, attributeKey, observedValue);
    }
}
