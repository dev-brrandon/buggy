package de.legitserver.assistant.observability.health;

import java.time.Clock;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Computes liveness/readiness without claiming unconfigured dependencies are healthy. */
public final class AssistantHealthService {
    private static final Set<DependencyId> REQUIRED = EnumSet.of(DependencyId.NETWORKCORE, DependencyId.DATABASE);

    private final DependencyHealthRegistry registry;
    private final Clock clock;

    public AssistantHealthService(DependencyHealthRegistry registry) {
        this(registry, Clock.systemUTC());
    }

    public AssistantHealthService(DependencyHealthRegistry registry, Clock clock) {
        if (registry == null || clock == null) throw new IllegalArgumentException("health dependency must not be null");
        this.registry = registry;
        this.clock = clock;
    }

    public AssistantHealthSnapshot snapshot() {
        Map<DependencyId, DependencyHealth> dependencies = registry.snapshot();
        boolean ready = REQUIRED.stream().allMatch(id -> dependencies.get(id).state() == DependencyState.UP);
        boolean degraded = dependencies.values().stream()
                .anyMatch(value -> value.state() != DependencyState.UP);
        AssistantHealthStatus status = !ready
                ? AssistantHealthStatus.NOT_READY
                : degraded ? AssistantHealthStatus.DEGRADED : AssistantHealthStatus.UP;
        return new AssistantHealthSnapshot(status, true, ready, clock.instant(), dependencies);
    }
}
