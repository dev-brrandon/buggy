package de.legitserver.assistant.supervisor.read;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.observability.audit.AuditAction;
import de.legitserver.assistant.observability.audit.AuditOutcome;
import de.legitserver.assistant.observability.audit.AuditRecord;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.observability.health.AssistantHealthStatus;
import de.legitserver.assistant.observability.health.DependencyHealth;
import de.legitserver.assistant.observability.health.DependencyHealthRegistry;
import de.legitserver.assistant.observability.health.DependencyId;
import de.legitserver.assistant.observability.health.DependencyState;
import de.legitserver.assistant.supervisor.health.AssistantFailureEvent;
import de.legitserver.assistant.supervisor.health.AssistantHealthReport;
import de.legitserver.assistant.supervisor.health.AssistantMetricsSnapshot;
import de.legitserver.assistant.supervisor.health.AssistantOperationalTelemetry;
import de.legitserver.assistant.supervisor.health.CapabilityDegradationRegistry;
import de.legitserver.assistant.supervisor.health.ToolFailureSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Security-gated, audit-gated read-only Supervisor implementation. */
public final class AssistantSupervisorReadService implements AssistantSupervisorReadPort {
    private static final Set<DependencyId> REQUIRED = EnumSet.of(DependencyId.NETWORKCORE, DependencyId.DATABASE);

    private final SupervisorAuthorizationPort authorization;
    private final AssistantOperationalTelemetry telemetry;
    private final DependencyHealthRegistry dependencies;
    private final CapabilityDegradationRegistry degradedCapabilities;
    private final AuditRecorder audit;
    private final Clock clock;

    public AssistantSupervisorReadService(
            SupervisorAuthorizationPort authorization,
            AssistantOperationalTelemetry telemetry,
            DependencyHealthRegistry dependencies,
            CapabilityDegradationRegistry degradedCapabilities,
            AuditRecorder audit,
            Clock clock) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
        this.degradedCapabilities = Objects.requireNonNull(degradedCapabilities, "degradedCapabilities");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AssistantHealthReport getAssistantHealth(SupervisorReadContext context, AssistantId targetAssistant) {
        return guarded(context, targetAssistant, "HEALTH", () -> {
            AssistantMetricsSnapshot metrics = telemetry.snapshot(targetAssistant);
            Map<DependencyId, DependencyHealth> dependencySnapshot = dependencies.snapshot();
            Set<AssistantCapabilityId> degraded = degradedCapabilities.snapshot(targetAssistant);
            return new AssistantHealthReport(
                    targetAssistant,
                    aggregateStatus(dependencySnapshot, degraded),
                    clock.instant(),
                    metrics,
                    dependencySnapshot,
                    degraded);
        });
    }

    @Override
    public AssistantMetricsSnapshot getAssistantMetrics(SupervisorReadContext context, AssistantId targetAssistant) {
        return guarded(context, targetAssistant, "METRICS", () -> telemetry.snapshot(targetAssistant));
    }

    @Override
    public List<AssistantFailureEvent> getRecentAssistantFailures(
            SupervisorReadContext context, AssistantId targetAssistant, int limit) {
        return guarded(context, targetAssistant, "FAILURES", () -> telemetry.recentFailures(targetAssistant, limit));
    }

    @Override
    public List<ToolFailureSummary> getToolFailureSummary(SupervisorReadContext context, AssistantId targetAssistant) {
        return guarded(context, targetAssistant, "TOOL_FAILURES", () -> telemetry.toolFailureSummary(targetAssistant));
    }

    @Override
    public Map<DependencyId, DependencyHealth> getDependencyStatus(
            SupervisorReadContext context, AssistantId targetAssistant) {
        return guarded(context, targetAssistant, "DEPENDENCIES", dependencies::snapshot);
    }

    @Override
    public Set<AssistantCapabilityId> getDegradedCapabilities(
            SupervisorReadContext context, AssistantId targetAssistant) {
        return guarded(context, targetAssistant, "DEGRADED_CAPABILITIES",
                () -> degradedCapabilities.snapshot(targetAssistant));
    }

    private <T> T guarded(
            SupervisorReadContext context, AssistantId targetAssistant, String readType, Supplier<T> supplier) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(targetAssistant, "targetAssistant");
        var decision = authorization.authorize(context, targetAssistant);
        Instant now = clock.instant();
        if (decision == null || !decision.allowed()) {
            audit(context, targetAssistant, readType, AuditOutcome.DENIED, now);
            throw new SupervisorAccessDeniedException();
        }
        audit(context, targetAssistant, readType, AuditOutcome.REQUESTED, now);
        T result = supplier.get();
        audit(context, targetAssistant, readType, AuditOutcome.SUCCESS, clock.instant());
        return result;
    }

    private void audit(
            SupervisorReadContext context,
            AssistantId targetAssistant,
            String readType,
            AuditOutcome outcome,
            Instant at) {
        audit.append(new AuditRecord(
                AuditAction.SUPERVISOR_READ,
                outcome,
                context.actor().requestId(),
                context.actor().traceId(),
                context.actor().actorId(),
                context.actor().minecraftUuid(),
                context.assistant().assistantId(),
                targetAssistant.name(),
                context.actor().serverId(),
                "ASSISTANT_SUPERVISOR",
                targetAssistant.name(),
                at,
                Map.of("readType", readType),
                true));
    }

    private static AssistantHealthStatus aggregateStatus(
            Map<DependencyId, DependencyHealth> dependencies,
            Set<AssistantCapabilityId> degraded) {
        boolean requiredDown = REQUIRED.stream().anyMatch(id -> dependencies.get(id).state() != DependencyState.UP);
        if (requiredDown) return AssistantHealthStatus.NOT_READY;
        boolean anyDegraded = dependencies.values().stream().anyMatch(value -> value.state() != DependencyState.UP);
        return anyDegraded || !degraded.isEmpty() ? AssistantHealthStatus.DEGRADED : AssistantHealthStatus.UP;
    }
}
