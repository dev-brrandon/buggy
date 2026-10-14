package de.legitserver.assistant.supervisor.read;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.observability.health.DependencyHealth;
import de.legitserver.assistant.observability.health.DependencyId;
import de.legitserver.assistant.supervisor.health.AssistantFailureEvent;
import de.legitserver.assistant.supervisor.health.AssistantHealthReport;
import de.legitserver.assistant.supervisor.health.AssistantMetricsSnapshot;
import de.legitserver.assistant.supervisor.health.ToolFailureSummary;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Canonical Phase-11 read-only Supervisor port. */
public interface AssistantSupervisorReadPort {
    AssistantHealthReport getAssistantHealth(SupervisorReadContext context, AssistantId targetAssistant);

    AssistantMetricsSnapshot getAssistantMetrics(SupervisorReadContext context, AssistantId targetAssistant);

    List<AssistantFailureEvent> getRecentAssistantFailures(
            SupervisorReadContext context, AssistantId targetAssistant, int limit);

    List<ToolFailureSummary> getToolFailureSummary(SupervisorReadContext context, AssistantId targetAssistant);

    Map<DependencyId, DependencyHealth> getDependencyStatus(SupervisorReadContext context, AssistantId targetAssistant);

    Set<AssistantCapabilityId> getDegradedCapabilities(SupervisorReadContext context, AssistantId targetAssistant);
}
