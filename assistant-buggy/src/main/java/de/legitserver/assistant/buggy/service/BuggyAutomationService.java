package de.legitserver.assistant.buggy.service;

import de.legitserver.assistant.buggy.activity.BuggyActivityEntry;
import de.legitserver.assistant.buggy.activity.BuggyActivityType;
import de.legitserver.assistant.buggy.automation.BuggyAutomationActivationGuard;
import de.legitserver.assistant.buggy.automation.BuggyAutomationExecutionPort;
import de.legitserver.assistant.buggy.automation.BuggyAutomationExecutionResult;
import de.legitserver.assistant.buggy.automation.BuggyAutomationLevel;
import de.legitserver.assistant.buggy.automation.BuggyAutomationRevision;
import de.legitserver.assistant.buggy.automation.BuggyAutomationRun;
import de.legitserver.assistant.buggy.automation.BuggyAutomationSignal;
import de.legitserver.assistant.buggy.automation.BuggyAutomationState;
import de.legitserver.assistant.buggy.repository.BuggyActivityRepository;
import de.legitserver.assistant.buggy.repository.BuggyAutomationRepository;
import de.legitserver.assistant.buggy.tools.BuggyToolApprovalPolicy;
import de.legitserver.assistant.buggy.tools.BuggyToolCatalog;
import de.legitserver.assistant.buggy.tools.BuggyToolDefinition;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.observability.audit.AuditAction;
import de.legitserver.assistant.observability.audit.AuditOutcome;
import de.legitserver.assistant.observability.audit.AuditRecord;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.pipeline.policy.RateLimitKey;
import de.legitserver.assistant.pipeline.policy.SlidingWindowRateLimiter;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
import de.legitserver.assistant.supervisor.selfhealing.SelfHealingBudgetTracker;
import de.legitserver.assistant.supervisor.selfhealing.SelfHealingPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** B6 proactive coordinator. It orchestrates only; privileged execution remains Foundation-owned. */
public final class BuggyAutomationService {
    private final BuggyAutomationRepository automations;
    private final BuggyActivityRepository activity;
    private final BuggyAutomationActivationGuard activationGuard;
    private final BuggyAutomationExecutionPort execution;
    private final SelfHealingBudgetTracker budgets;
    private final SlidingWindowRateLimiter rateLimiter;
    private final AuditRecorder audit;
    private final Clock clock;

    public BuggyAutomationService(
            BuggyAutomationRepository automations,
            BuggyActivityRepository activity,
            BuggyAutomationActivationGuard activationGuard,
            BuggyAutomationExecutionPort execution,
            SelfHealingBudgetTracker budgets,
            SlidingWindowRateLimiter rateLimiter,
            AuditRecorder audit,
            Clock clock) {
        this.automations = Objects.requireNonNull(automations, "automations");
        this.activity = Objects.requireNonNull(activity, "activity");
        this.activationGuard = Objects.requireNonNull(activationGuard, "activationGuard");
        this.execution = Objects.requireNonNull(execution, "execution");
        this.budgets = Objects.requireNonNull(budgets, "budgets");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BuggyAutomationRevision save(long expectedRevision, BuggyAutomationRevision revision) {
        validateCatalogBinding(revision);
        if (revision.state() == BuggyAutomationState.ENABLED && revision.riskLevel() != ToolRiskLevel.LOW
                && !activationGuard.approved(revision)) {
            throw new IllegalStateException("privileged automation activation is not approved by Foundation Requests/Approvals");
        }
        return automations.append(expectedRevision, revision);
    }

    public List<BuggyAutomationRun> observe(BuggyAutomationSignal signal) {
        Objects.requireNonNull(signal, "signal");
        if (signal.healthy()) return List.of();
        activity.append(activity(signal, null, BuggyActivityType.OBSERVATION_RECEIVED, "Observation received", Map.of("signal", signal.type().name())));
        return runMatching(automations.enabledForSignal(signal.type()), signal);
    }

    public List<BuggyAutomationRun> runDue(Instant at, BuggyAutomationSignal scheduleSignalTemplate) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(scheduleSignalTemplate, "scheduleSignalTemplate");
        if (scheduleSignalTemplate.healthy()) return List.of();
        return runMatching(automations.dueScheduled(at), scheduleSignalTemplate);
    }

    private List<BuggyAutomationRun> runMatching(List<BuggyAutomationRevision> candidates, BuggyAutomationSignal signal) {
        ArrayList<BuggyAutomationRun> runs = new ArrayList<>();
        for (BuggyAutomationRevision automation : candidates) {
            if (automation.state() != BuggyAutomationState.ENABLED) continue;
            runs.add(runOne(automation, signal));
        }
        return List.copyOf(runs);
    }

    private BuggyAutomationRun runOne(BuggyAutomationRevision automation, BuggyAutomationSignal signal) {
        Instant started = clock.instant();
        if (!rateLimiter.tryAcquire(new RateLimitKey(signal.actor().actorId(), AssistantId.BUGGY, automation.capabilityId()))) {
            return persist(automation, signal, BuggyAutomationLevel.OBSERVE, false, "RATE_LIMITED", started, BuggyActivityType.AUTOMATION_SUPPRESSED);
        }
        SelfHealingPolicy budgetPolicy = new SelfHealingPolicy(
                automation.maxAttemptsPerTarget(), automation.cooldown(), automation.window(), automation.maxActionsPerWindow(),
                automation.sameTargetSuppression(), automation.failureEscalationThreshold());
        SelfHealingBudgetTracker.Reservation reservation = budgets.reserve(
                AssistantId.BUGGY, automation.automationId() + ":" + signal.targetFingerprint(), started, budgetPolicy);
        if (!reservation.allowed()) {
            return persist(automation, signal, BuggyAutomationLevel.OBSERVE, false,
                    "BUDGET_" + reservation.denialReason().name(), started, BuggyActivityType.AUTOMATION_SUPPRESSED);
        }

        BuggyAutomationLevel effective = effectiveLevel(automation);
        BuggyAutomationExecutionResult result = execution.execute(automation, signal, effective);
        int failures = budgets.recordOutcome(AssistantId.BUGGY,
                automation.automationId() + ":" + signal.targetFingerprint(), result.successful());
        BuggyAutomationLevel finalLevel = effective;
        BuggyActivityType type = typeFor(effective, result.successful());
        boolean finalSuccess = result.successful();
        String code = result.safeCode();
        if (!result.successful() && failures >= automation.failureEscalationThreshold()
                && effective != BuggyAutomationLevel.ESCALATE) {
            BuggyAutomationExecutionResult escalated = execution.execute(automation, signal, BuggyAutomationLevel.ESCALATE);
            finalLevel = BuggyAutomationLevel.ESCALATE;
            finalSuccess = escalated.successful();
            type = escalated.successful() ? BuggyActivityType.AUTOMATION_ESCALATED : BuggyActivityType.AUTOMATION_FAILED;
            code = escalated.safeCode();
        }
        return persist(automation, signal, finalLevel, finalSuccess, code, started, type);
    }

    private static BuggyAutomationLevel effectiveLevel(BuggyAutomationRevision automation) {
        if (automation.riskLevel() != ToolRiskLevel.LOW && automation.maxLevel().ordinal() >= BuggyAutomationLevel.ACT.ordinal()) {
            return BuggyAutomationLevel.ESCALATE;
        }
        return automation.maxLevel();
    }

    private BuggyAutomationRun persist(BuggyAutomationRevision automation, BuggyAutomationSignal signal,
            BuggyAutomationLevel level, boolean success, String safeCode, Instant started, BuggyActivityType type) {
        BuggyAutomationRun run = new BuggyAutomationRun(UUID.randomUUID(), automation.automationId(), automation.revision(),
                level, signal.targetFingerprint(), success, safeCode, started, clock.instant());
        automations.appendRun(run);
        auditRun(signal, automation, run, type);
        activity.append(activity(signal, automation, type, "Automation " + level.name().toLowerCase(java.util.Locale.ROOT),
                Map.of("result", safeCode, "risk", automation.riskLevel().name(), "tool", automation.actionToolId().value())));
        return run;
    }

    private void auditRun(BuggyAutomationSignal signal, BuggyAutomationRevision automation, BuggyAutomationRun run, BuggyActivityType type) {
        boolean critical = automation.riskLevel().isHighRisk() || run.finalLevel() == BuggyAutomationLevel.ESCALATE;
        audit.append(new AuditRecord(
                AuditAction.BUGGY_AUTOMATION,
                run.successful() ? AuditOutcome.SUCCESS : AuditOutcome.FAILED,
                signal.actor().requestId(),
                signal.actor().traceId(),
                signal.actor().actorId(),
                signal.actor().minecraftUuid(),
                AssistantId.BUGGY,
                automation.automationId(),
                signal.actor().serverId(),
                "BUGGY_AUTOMATION_RUN",
                run.runId().toString(),
                run.completedAt(),
                Map.of(
                        "activityType", type.name(),
                        "level", run.finalLevel().name(),
                        "risk", automation.riskLevel().name(),
                        "safeCode", run.safeCode(),
                        "tool", automation.actionToolId().value()),
                critical));
    }

    private static BuggyActivityType typeFor(BuggyAutomationLevel level, boolean successful) {
        if (!successful) return BuggyActivityType.AUTOMATION_FAILED;
        return switch (level) {
            case OBSERVE, ANALYZE -> BuggyActivityType.AUTOMATION_ANALYZED;
            case PREPARE -> BuggyActivityType.AUTOMATION_PREPARED;
            case ACT -> BuggyActivityType.AUTOMATION_ACTED;
            case ESCALATE -> BuggyActivityType.AUTOMATION_ESCALATED;
        };
    }

    private BuggyActivityEntry activity(BuggyAutomationSignal signal, BuggyAutomationRevision automation,
            BuggyActivityType type, String summary, Map<String, String> details) {
        return new BuggyActivityEntry(UUID.randomUUID(), type,
                automation == null ? de.legitserver.assistant.buggy.activity.BuggyAttentionLevel.INFO : automation.attention(),
                null, automation == null ? null : automation.automationId(), summary, details, signal.actor().actorId(), clock.instant());
    }

    private static void validateCatalogBinding(BuggyAutomationRevision revision) {
        BuggyToolDefinition tool = BuggyToolCatalog.byId().get(revision.actionToolId());
        if (tool == null) throw new IllegalArgumentException("Automation tool is not in buggy-tools-v1");
        if (!tool.descriptor().capability().equals(revision.capabilityId())) throw new IllegalArgumentException("Automation capability/tool mismatch");
        if (revision.riskLevel().ordinal() < tool.descriptor().riskLevel().ordinal()) throw new IllegalArgumentException("Automation risk may not understate tool risk");
        if (revision.maxLevel() == BuggyAutomationLevel.ACT) {
            if (tool.approvalPolicy() != BuggyToolApprovalPolicy.AUTONOMOUS_LOW || tool.descriptor().riskLevel() != ToolRiskLevel.LOW) {
                throw new IllegalArgumentException("ACT requires an AUTONOMOUS_LOW LOW-risk tool");
            }
        }
    }
}
