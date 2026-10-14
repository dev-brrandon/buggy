package de.legitserver.assistant.supervisor.selfhealing;

import de.legitserver.assistant.core.assistant.AssistantCapabilities;
import de.legitserver.assistant.observability.audit.AuditAction;
import de.legitserver.assistant.observability.audit.AuditOutcome;
import de.legitserver.assistant.observability.audit.AuditRecord;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.observability.metrics.AssistantMetric;
import de.legitserver.assistant.observability.metrics.AssistantMetrics;
import de.legitserver.assistant.runtime.execution.ToolRuntime;
import de.legitserver.assistant.runtime.execution.ToolRuntimeException;
import de.legitserver.assistant.runtime.model.ToolAuditPolicy;
import de.legitserver.assistant.runtime.model.ToolDescriptor;
import de.legitserver.assistant.runtime.model.ToolInvocation;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
import de.legitserver.assistant.runtime.policy.ToolInvocationCanonicalizer;
import de.legitserver.assistant.runtime.registry.ToolRegistry;
import de.legitserver.assistant.supervisor.health.AssistantOperationalTelemetry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/** Typed-only automatic Self-Healing orchestrator layered on the Phase-10 Tool Runtime. */
public final class SelfHealingService {
    private final ToolRegistry registry;
    private final ToolRuntime runtime;
    private final SelfHealingPolicy policy;
    private final SelfHealingBudgetTracker budget;
    private final SelfHealingEscalationPort escalation;
    private final AssistantOperationalTelemetry telemetry;
    private final AssistantMetrics metrics;
    private final AuditRecorder audit;
    private final Clock clock;

    public SelfHealingService(
            ToolRegistry registry,
            ToolRuntime runtime,
            SelfHealingPolicy policy,
            SelfHealingBudgetTracker budget,
            SelfHealingEscalationPort escalation,
            AssistantOperationalTelemetry telemetry,
            AssistantMetrics metrics,
            AuditRecorder audit,
            Clock clock) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.escalation = Objects.requireNonNull(escalation, "escalation");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public SelfHealingResult attempt(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        ToolDescriptor descriptor = registry.find(invocation.toolId()).map(value -> value.descriptor()).orElse(null);
        if (descriptor == null) return SelfHealingResult.suppressed(SelfHealingSuppressionReason.TOOL_NOT_REGISTERED);
        SelfHealingSuppressionReason eligibility = eligibilityFailure(descriptor);
        if (eligibility != null) return SelfHealingResult.suppressed(eligibility);

        String fingerprint = targetFingerprint(invocation);
        Instant started = clock.instant();
        SelfHealingBudgetTracker.Reservation reservation = budget.reserve(
                invocation.assistant().assistantId(), fingerprint, started, policy);
        if (!reservation.allowed()) return SelfHealingResult.suppressed(reservation.denialReason());

        metrics.increment(AssistantMetric.SELF_HEALING_ATTEMPTS);
        audit(invocation, descriptor, AuditOutcome.REQUESTED, "SELF_HEAL_REQUESTED", started, fingerprint);
        try {
            var result = runtime.invoke(invocation);
            budget.recordOutcome(invocation.assistant().assistantId(), fingerprint, true);
            telemetry.recordToolCall(
                    invocation.assistant().assistantId(), descriptor.toolId(), descriptor.capability(),
                    result.duration(), null, safeTarget(invocation), clock.instant());
            audit(invocation, descriptor, AuditOutcome.SUCCESS, "SELF_HEAL_SUCCEEDED", clock.instant(), fingerprint);
            return new SelfHealingResult(SelfHealingStatus.SUCCEEDED, "SELF_HEAL_SUCCEEDED", result, null);
        } catch (ToolRuntimeException failure) {
            metrics.increment(AssistantMetric.SELF_HEALING_FAILURES);
            int consecutiveFailures = budget.recordOutcome(invocation.assistant().assistantId(), fingerprint, false);
            Instant failedAt = clock.instant();
            telemetry.recordToolCall(
                    invocation.assistant().assistantId(), descriptor.toolId(), descriptor.capability(),
                    nonNegativeDuration(started, failedAt), failure.code(), safeTarget(invocation), failedAt);
            telemetry.recordSelfHealingFailure(
                    invocation.assistant().assistantId(), descriptor.toolId(), descriptor.capability(), failure.code().name(),
                    safeTarget(invocation), failedAt);
            audit(invocation, descriptor, AuditOutcome.FAILED, failure.code().name(), failedAt, fingerprint);
            if (consecutiveFailures >= policy.failureEscalationThreshold()) {
                SelfHealingEscalationReceipt receipt = escalation.escalate(new SelfHealingEscalation(
                        invocation.assistant().assistantId(), descriptor.toolId(), descriptor.capability(), fingerprint,
                        consecutiveFailures, failure.code().name(), failedAt));
                audit(invocation, descriptor, AuditOutcome.FAILED, "SELF_HEAL_ESCALATED", failedAt, fingerprint);
                return new SelfHealingResult(SelfHealingStatus.ESCALATED, failure.code().name(), null, receipt);
            }
            return new SelfHealingResult(SelfHealingStatus.FAILED, failure.code().name(), null, null);
        }
    }

    private static SelfHealingSuppressionReason eligibilityFailure(ToolDescriptor descriptor) {
        if (descriptor.riskLevel() != ToolRiskLevel.LOW) return SelfHealingSuppressionReason.RISK_NOT_LOW;
        if (!descriptor.selfHealingEligible()) return SelfHealingSuppressionReason.NOT_SELF_HEALING_ELIGIBLE;
        if (!descriptor.capability().equals(AssistantCapabilities.SELF_HEAL_LOW)) {
            return SelfHealingSuppressionReason.CAPABILITY_NOT_SELF_HEAL;
        }
        if (descriptor.auditPolicy() != ToolAuditPolicy.CRITICAL_REQUIRED) {
            return SelfHealingSuppressionReason.AUDIT_NOT_FAIL_CLOSED;
        }
        return null;
    }

    private void audit(
            ToolInvocation invocation,
            ToolDescriptor descriptor,
            AuditOutcome outcome,
            String stage,
            Instant at,
            String fingerprint) {
        audit.append(new AuditRecord(
                AuditAction.SELF_HEALING,
                outcome,
                invocation.actor().requestId(),
                invocation.actor().traceId(),
                invocation.actor().actorId(),
                invocation.actor().minecraftUuid(),
                invocation.assistant().assistantId(),
                descriptor.toolId().value(),
                invocation.actor().serverId(),
                "SELF_HEAL_ACTION",
                fingerprint,
                at,
                Map.of(
                        "stage", stage,
                        "capability", descriptor.capability().value(),
                        "risk", descriptor.riskLevel().name(),
                        "resourceType", invocation.resource().resourceType()),
                true));
    }

    private static String targetFingerprint(ToolInvocation invocation) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ToolInvocationCanonicalizer.canonicalPayload(invocation)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String safeTarget(ToolInvocation invocation) {
        return invocation.resource().resourceType() + ":" + invocation.resource().resourceId();
    }

    private static Duration nonNegativeDuration(Instant start, Instant end) {
        Duration duration = Duration.between(start, end);
        return duration.isNegative() ? Duration.ZERO : duration;
    }
}
