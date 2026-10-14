package de.legitserver.assistant.buggy.memory;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.UUID;

/** Exactly one terminal decision may be persisted for a memory candidate. */
public record BuggyMemoryCandidateDecision(
        UUID candidateId,
        BuggyMemoryCandidateDecisionType decision,
        BuggyMemoryId memoryId,
        String reason,
        ActorId decidedBy,
        Instant decidedAt) {

    public BuggyMemoryCandidateDecision {
        candidateId = CoreValidation.requireNonNull(candidateId, "candidateId");
        decision = CoreValidation.requireNonNull(decision, "decision");
        reason = CoreValidation.requireText(reason, "reason", 1024);
        decidedBy = CoreValidation.requireNonNull(decidedBy, "decidedBy");
        decidedAt = CoreValidation.requireNonNull(decidedAt, "decidedAt");
        if ((decision == BuggyMemoryCandidateDecisionType.ACCEPTED) != (memoryId != null)) {
            throw new IllegalArgumentException("Accepted memory candidates require memoryId; rejected candidates must not have one");
        }
    }
}
