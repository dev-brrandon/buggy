package de.legitserver.assistant.buggy.supervision;

import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.supervisor.read.AssistantSupervisorReadPort;
import de.legitserver.assistant.supervisor.read.SupervisorReadContext;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** B11 orchestration facade. Foundation Supervisor stays authoritative for actual reads. */
public final class BuggySupervisorReadinessService {
    private final BuggyFutureAssistantDirectoryPort directory;
    private final AssistantSupervisorReadPort foundationSupervisor;
    private final Clock clock;

    public BuggySupervisorReadinessService(
            BuggyFutureAssistantDirectoryPort directory,
            AssistantSupervisorReadPort foundationSupervisor,
            Clock clock) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.foundationSupervisor = Objects.requireNonNull(foundationSupervisor, "foundationSupervisor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BuggyFutureAssistantReadiness inspect(SupervisorReadContext context, AssistantId targetAssistant) {
        Objects.requireNonNull(context, "context");
        BuggySupervisorContractCatalog.requireFutureTarget(targetAssistant);
        BuggyFutureAssistantPresence presence = Objects.requireNonNull(directory.resolve(targetAssistant), "directory presence");
        if (presence.assistantId() != targetAssistant) throw new IllegalStateException("future assistant directory returned mismatched target");
        var health = presence.supervisorReadable()
                ? foundationSupervisor.getAssistantHealth(context, targetAssistant)
                : null;
        return new BuggyFutureAssistantReadiness(targetAssistant, presence, health, false, false, false);
    }

    public BuggySupervisorCommandPlan plan(AssistantId targetAssistant, BuggySupervisorOperation operation, String reason) {
        BuggySupervisorContractCatalog.requireFutureTarget(targetAssistant);
        Objects.requireNonNull(operation, "operation");
        if (operation.isForbidden()) throw new IllegalArgumentException("Buggy cannot plan forbidden supervisor operation");
        BuggyFutureAssistantContract contract = BuggySupervisorContractCatalog.contract(targetAssistant);
        return new BuggySupervisorCommandPlan(
                targetAssistant,
                operation,
                contract.operations().get(operation),
                operation.toolId(),
                reason,
                true,
                false);
    }

    public BuggyDelegationEnvelope prepareDelegation(
            de.legitserver.assistant.buggy.tasks.BuggyTaskId sourceTaskId,
            AssistantId targetAssistant,
            String objective,
            List<String> contextRefs,
            List<String> evidenceRefs,
            List<String> expectedOutputs) {
        BuggySupervisorContractCatalog.requireFutureTarget(targetAssistant);
        BuggyFutureAssistantPresence presence = Objects.requireNonNull(directory.resolve(targetAssistant), "directory presence");
        if (!presence.delegationAvailable()) {
            throw new IllegalStateException("target assistant is not available for delegation: " + presence.state());
        }
        BuggyDelegationIntent intent = new BuggyDelegationIntent(
                UUID.randomUUID(), sourceTaskId, targetAssistant, objective, contextRefs, evidenceRefs, expectedOutputs, clock.instant());
        return new BuggyDelegationEnvelope(AssistantId.BUGGY, intent, true, true, true, false);
    }

    public BuggyDelegationReview review(BuggyDelegationEnvelope envelope, BuggyDelegationResult result) {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(result, "result");
        if (!envelope.intent().delegationId().equals(result.delegationId())) {
            throw new IllegalArgumentException("delegation result id mismatch");
        }
        if (envelope.intent().targetAssistant() != result.targetAssistant()) {
            throw new IllegalArgumentException("delegation result target mismatch");
        }
        BuggyDelegationReviewStatus status = switch (result.outcome()) {
            case COMPLETED -> result.disagreement() == null
                    ? BuggyDelegationReviewStatus.ACCEPTED
                    : BuggyDelegationReviewStatus.FOLLOW_UP_REQUIRED;
            case PARTIAL -> BuggyDelegationReviewStatus.FOLLOW_UP_REQUIRED;
            case REJECTED, BLOCKED -> BuggyDelegationReviewStatus.ESCALATION_REQUIRED;
        };
        return new BuggyDelegationReview(result, status, true);
    }
}
