package de.legitserver.assistant.buggy.supervision;

import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Result remains attributable to the independent target assistant. */
public record BuggyDelegationResult(
        UUID delegationId,
        AssistantId targetAssistant,
        BuggyDelegationOutcome outcome,
        String summary,
        List<String> evidenceRefs,
        String disagreement,
        Instant completedAt) {
    public BuggyDelegationResult {
        delegationId = Objects.requireNonNull(delegationId, "delegationId");
        BuggySupervisorContractCatalog.requireFutureTarget(targetAssistant);
        outcome = Objects.requireNonNull(outcome, "outcome");
        summary = CoreValidation.requireText(summary, "summary", 4096);
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        if (evidenceRefs.size() > 128) throw new IllegalArgumentException("evidenceRefs exceeds max items 128");
        evidenceRefs = evidenceRefs.stream().map(value -> CoreValidation.requireSafeToken(value, "evidenceRef", 256)).toList();
        disagreement = CoreValidation.optionalText(disagreement, "disagreement", 4096);
        completedAt = Objects.requireNonNull(completedAt, "completedAt");
    }
}
