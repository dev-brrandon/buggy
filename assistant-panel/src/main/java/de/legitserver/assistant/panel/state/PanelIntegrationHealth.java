package de.legitserver.assistant.panel.state;

import java.time.Instant;
import java.util.Map;

/** Aggregate degraded-state projection consumed by the Panel instead of guessing from individual errors. */
public record PanelIntegrationHealth(
        PanelIntegrationState state,
        Map<PanelSurface, PanelSurfaceStatus> surfaces,
        Instant checkedAt) {
    public PanelIntegrationHealth {
        if (state == null || surfaces == null || checkedAt == null) throw new IllegalArgumentException("integration health fields required");
        surfaces = Map.copyOf(surfaces);
        for (PanelSurface surface : PanelSurface.values()) {
            if (!surfaces.containsKey(surface)) throw new IllegalArgumentException("missing Panel surface status " + surface);
        }
    }
}
