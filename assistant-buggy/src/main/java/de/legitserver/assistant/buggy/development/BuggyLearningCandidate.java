package de.legitserver.assistant.buggy.development;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.UUID;

/** G1.5 only proposes learning. Application to behavior/personality/maturity is deferred to G1.9. */
public record BuggyLearningCandidate(
        UUID candidateId,
        UUID reflectionId,
        String ruleKey,
        String proposal,
        int confidenceBps,
        ActorId createdBy,
        Instant createdAt) {

    public BuggyLearningCandidate {
        candidateId = CoreValidation.requireNonNull(candidateId, "candidateId");
        reflectionId = CoreValidation.requireNonNull(reflectionId, "reflectionId");
        ruleKey = CoreValidation.requireLowerDottedId(ruleKey, "ruleKey", 128);
        proposal = CoreValidation.requireText(proposal, "proposal", 4096);
        if (confidenceBps < 0 || confidenceBps > 10_000) {
            throw new IllegalArgumentException("confidenceBps must be between 0 and 10000");
        }
        createdBy = CoreValidation.requireNonNull(createdBy, "createdBy");
        createdAt = CoreValidation.requireNonNull(createdAt, "createdAt");
    }
}
