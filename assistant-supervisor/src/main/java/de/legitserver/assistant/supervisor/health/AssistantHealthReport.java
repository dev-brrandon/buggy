package de.legitserver.assistant.supervisor.health;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.observability.health.AssistantHealthStatus;
import de.legitserver.assistant.observability.health.DependencyHealth;
import de.legitserver.assistant.observability.health.DependencyId;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** Read-only aggregate health projection for one Assistant identity. */
public record AssistantHealthReport(
        AssistantId assistantId,
        AssistantHealthStatus status,
        Instant capturedAt,
        AssistantMetricsSnapshot metrics,
        Map<DependencyId, DependencyHealth> dependencyHealth,
        Set<AssistantCapabilityId> degradedCapabilities) {
    public AssistantHealthReport {
        if (assistantId == null || status == null || capturedAt == null || metrics == null) {
            throw new IllegalArgumentException("health report fields must not be null");
        }
        dependencyHealth = dependencyHealth == null ? Map.of() : Map.copyOf(dependencyHealth);
        degradedCapabilities = degradedCapabilities == null ? Set.of() : Set.copyOf(degradedCapabilities);
    }
}
