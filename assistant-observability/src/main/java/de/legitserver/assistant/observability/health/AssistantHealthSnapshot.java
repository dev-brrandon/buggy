package de.legitserver.assistant.observability.health;

import java.time.Instant;
import java.util.Map;

/** Read-only aggregate and dependency health projection for internal supervisor use. */
public record AssistantHealthSnapshot(
        AssistantHealthStatus status,
        boolean live,
        boolean ready,
        Instant capturedAt,
        Map<DependencyId, DependencyHealth> dependencies) {
    public AssistantHealthSnapshot {
        if (status == null || capturedAt == null) throw new IllegalArgumentException("health fields must not be null");
        dependencies = dependencies == null ? Map.of() : Map.copyOf(dependencies);
    }
}
