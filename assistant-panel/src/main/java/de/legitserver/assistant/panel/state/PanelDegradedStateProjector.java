package de.legitserver.assistant.panel.state;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/** Deterministic aggregate projector; one degraded surface does not falsely mark every surface down. */
public final class PanelDegradedStateProjector {
    public PanelIntegrationHealth project(Map<PanelSurface, PanelSurfaceStatus> supplied, Instant checkedAt) {
        if (supplied == null || checkedAt == null) throw new IllegalArgumentException("supplied and checkedAt required");
        EnumMap<PanelSurface, PanelSurfaceStatus> statuses = new EnumMap<>(PanelSurface.class);
        for (PanelSurface surface : PanelSurface.values()) {
            statuses.put(surface, supplied.getOrDefault(surface,
                    new PanelSurfaceStatus(surface, PanelSurfaceAvailability.UNAVAILABLE, "STATUS_NOT_REPORTED")));
        }
        long unavailable = statuses.values().stream()
                .filter(value -> value.availability() == PanelSurfaceAvailability.UNAVAILABLE).count();
        boolean degraded = statuses.values().stream()
                .anyMatch(value -> value.availability() == PanelSurfaceAvailability.DEGRADED);
        PanelIntegrationState state;
        if (unavailable == statuses.size()) state = PanelIntegrationState.UNAVAILABLE;
        else if (unavailable > 0 || degraded) state = PanelIntegrationState.DEGRADED;
        else state = PanelIntegrationState.AVAILABLE;
        return new PanelIntegrationHealth(state, statuses, checkedAt);
    }
}
