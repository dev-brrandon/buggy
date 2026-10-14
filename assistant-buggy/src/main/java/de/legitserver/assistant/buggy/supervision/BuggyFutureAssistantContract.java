package de.legitserver.assistant.buggy.supervision;

import de.legitserver.assistant.core.assistant.AssistantId;
import java.util.Map;
import java.util.Objects;

/** Versioned supervisor contract only. It deliberately contains no target identity/personality profile. */
public record BuggyFutureAssistantContract(
        String contractVersion,
        AssistantId targetAssistant,
        Map<BuggySupervisorOperation, BuggySupervisorAuthorityMode> operations,
        BuggySupervisorOwnershipBoundary ownershipBoundary) {
    public BuggyFutureAssistantContract {
        if (!"buggy-supervisor-contract-v1".equals(contractVersion)) {
            throw new IllegalArgumentException("unsupported supervisor contract version");
        }
        BuggySupervisorContractCatalog.requireFutureTarget(targetAssistant);
        operations = Map.copyOf(Objects.requireNonNull(operations, "operations"));
        ownershipBoundary = Objects.requireNonNull(ownershipBoundary, "ownershipBoundary");
        for (BuggySupervisorOperation operation : BuggySupervisorOperation.values()) {
            if (operations.get(operation) != operation.authorityMode()) {
                throw new IllegalArgumentException("operation contract mismatch: " + operation.name());
            }
        }
        if (!ownershipBoundary.targetMayDisagree() || !ownershipBoundary.targetMustReauthorizeDelegatedWork()) {
            throw new IllegalArgumentException("future assistant independence cannot be weakened");
        }
    }
}
