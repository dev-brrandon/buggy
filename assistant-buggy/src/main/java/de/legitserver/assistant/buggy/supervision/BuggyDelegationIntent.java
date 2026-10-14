package de.legitserver.assistant.buggy.supervision;

import de.legitserver.assistant.buggy.tasks.BuggyTaskId;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Context-only delegation intent. It grants no permissions and carries no target personality/identity mutation. */
public record BuggyDelegationIntent(
        UUID delegationId,
        BuggyTaskId sourceTaskId,
        AssistantId targetAssistant,
        String objective,
        List<String> contextRefs,
        List<String> evidenceRefs,
        List<String> expectedOutputs,
        Instant createdAt) {
    public BuggyDelegationIntent {
        delegationId = Objects.requireNonNull(delegationId, "delegationId");
        sourceTaskId = Objects.requireNonNull(sourceTaskId, "sourceTaskId");
        BuggySupervisorContractCatalog.requireFutureTarget(targetAssistant);
        objective = CoreValidation.requireText(objective, "objective", 4096);
        contextRefs = safeRefs(contextRefs, "contextRefs", 64);
        evidenceRefs = safeRefs(evidenceRefs, "evidenceRefs", 128);
        expectedOutputs = safeTextList(expectedOutputs, "expectedOutputs", 32, 1024);
        if (expectedOutputs.isEmpty()) throw new IllegalArgumentException("expectedOutputs must not be empty");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    private static List<String> safeRefs(List<String> values, String field, int maxItems) {
        List<String> copy = values == null ? List.of() : List.copyOf(values);
        if (copy.size() > maxItems) throw new IllegalArgumentException(field + " exceeds max items " + maxItems);
        return copy.stream().map(value -> CoreValidation.requireSafeToken(value, field, 256)).toList();
    }

    private static List<String> safeTextList(List<String> values, String field, int maxItems, int maxLength) {
        List<String> copy = values == null ? List.of() : List.copyOf(values);
        if (copy.size() > maxItems) throw new IllegalArgumentException(field + " exceeds max items " + maxItems);
        return copy.stream().map(value -> CoreValidation.requireText(value, field, maxLength)).toList();
    }
}
