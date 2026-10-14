package de.legitserver.assistant.pipeline.model;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.observability.health.DependencyId;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/** Server-side route descriptor with explicit dependency and timeout bounds. */
public record PipelineRoute(
        String routeId,
        PipelineRouteType routeType,
        AssistantCapabilityId capabilityId,
        Set<DependencyId> requiredDependencies,
        Duration executionTimeout) {
    public PipelineRoute {
        routeId = CoreValidation.requireSafeToken(routeId, "routeId", 128);
        routeType = Objects.requireNonNull(routeType, "routeType");
        capabilityId = Objects.requireNonNull(capabilityId, "capabilityId");
        requiredDependencies = requiredDependencies == null ? Set.of() : Set.copyOf(requiredDependencies);
        executionTimeout = Objects.requireNonNull(executionTimeout, "executionTimeout");
        if (executionTimeout.isZero() || executionTimeout.isNegative() || executionTimeout.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalArgumentException("executionTimeout must be > 0 and <= 2 minutes");
        }
    }
}
