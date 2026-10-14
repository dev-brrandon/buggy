package de.legitserver.assistant.supervisor.health;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.runtime.execution.ToolFailureCode;
import de.legitserver.assistant.runtime.model.ToolId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Bounded per-assistant telemetry boundary consumed by the Phase-11 Supervisor. */
public interface AssistantOperationalTelemetry {
    void recordRequest(AssistantId assistantId, Duration latency, boolean success);

    void recordToolCall(
            AssistantId assistantId,
            ToolId toolId,
            AssistantCapabilityId capability,
            Duration latency,
            ToolFailureCode failureCode,
            String target,
            Instant at);

    void recordRateLimitHit(AssistantId assistantId, String target, Instant at);

    void recordAuthorizationDenial(AssistantId assistantId, AssistantCapabilityId capability, String target, Instant at);

    void recordSelfHealingFailure(
            AssistantId assistantId,
            ToolId toolId,
            AssistantCapabilityId capability,
            String safeCode,
            String target,
            Instant at);

    void setActiveConversations(AssistantId assistantId, long value);

    void setQueueDepth(AssistantId assistantId, long value);

    AssistantMetricsSnapshot snapshot(AssistantId assistantId);

    List<AssistantFailureEvent> recentFailures(AssistantId assistantId, int limit);

    List<ToolFailureSummary> toolFailureSummary(AssistantId assistantId);
}
