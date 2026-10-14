package de.legitserver.assistant.observability.health;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe dependency state registry initialized to NOT_CONFIGURED for every known dependency. */
public final class DependencyHealthRegistry {
    private final Clock clock;
    private final ConcurrentHashMap<DependencyId, DependencyHealth> states = new ConcurrentHashMap<>();

    public DependencyHealthRegistry() {
        this(Clock.systemUTC());
    }

    public DependencyHealthRegistry(Clock clock) {
        if (clock == null) throw new IllegalArgumentException("clock must not be null");
        this.clock = clock;
        Instant now = clock.instant();
        for (DependencyId dependency : DependencyId.values()) {
            states.put(dependency, DependencyHealth.notConfigured(dependency, now));
        }
    }

    public void update(DependencyId dependency, DependencyState state, String safeCode, long latencyMs) {
        states.put(dependency, new DependencyHealth(dependency, state, safeCode, latencyMs, clock.instant()));
    }

    public DependencyHealth get(DependencyId dependency) {
        if (dependency == null) throw new IllegalArgumentException("dependency must not be null");
        return states.get(dependency);
    }

    public Map<DependencyId, DependencyHealth> snapshot() {
        EnumMap<DependencyId, DependencyHealth> copy = new EnumMap<>(DependencyId.class);
        copy.putAll(states);
        return Map.copyOf(copy);
    }
}
