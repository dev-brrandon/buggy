package de.legitserver.assistant.bootstrap.observability;

import de.legitserver.assistant.observability.health.AssistantHealthService;
import de.legitserver.assistant.observability.health.AssistantHealthSnapshot;
import de.legitserver.assistant.observability.health.DependencyHealth;
import de.legitserver.assistant.observability.health.DependencyId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/** Local-management health contributor with sanitized dependency details. */
public final class Phase6DependencyHealthIndicator implements HealthIndicator {
    private final AssistantHealthService health;

    public Phase6DependencyHealthIndicator(AssistantHealthService health) {
        if (health == null) throw new IllegalArgumentException("health must not be null");
        this.health = health;
    }

    @Override
    public Health health() {
        AssistantHealthSnapshot snapshot = health.snapshot();
        Map<String, Object> details = new LinkedHashMap<>();
        for (Map.Entry<DependencyId, DependencyHealth> entry : snapshot.dependencies().entrySet()) {
            DependencyHealth dependency = entry.getValue();
            details.put(
                    entry.getKey().name(),
                    Map.of(
                            "displayName", entry.getKey().displayName(),
                            "state", dependency.state().name(),
                            "safeCode", dependency.safeCode(),
                            "latencyMs", dependency.latencyMs(),
                            "checkedAt", dependency.checkedAt().toString()));
        }
        Health.Builder builder = snapshot.ready() ? Health.up() : Health.outOfService();
        return builder
                .withDetail("assistantStatus", snapshot.status().name())
                .withDetail("ready", snapshot.ready())
                .withDetail("dependencies", Map.copyOf(details))
                .build();
    }
}
