package de.legitserver.assistant.api.v1.panel;

import de.legitserver.assistant.api.v1.error.ApiException;
import de.legitserver.assistant.api.v1.model.ApiCallContext;
import de.legitserver.assistant.api.v1.model.ApiResourceView;
import de.legitserver.assistant.api.v1.pagination.ApiPage;
import de.legitserver.assistant.api.v1.pagination.ApiPageRequest;
import de.legitserver.assistant.panel.state.PanelDegradedStateProjector;
import de.legitserver.assistant.panel.state.PanelIntegrationHealth;
import de.legitserver.assistant.panel.state.PanelSurface;
import de.legitserver.assistant.panel.state.PanelSurfaceAvailability;
import de.legitserver.assistant.panel.state.PanelSurfaceStatus;
import java.time.Clock;
import java.util.EnumMap;
import org.springframework.http.HttpStatus;

/** Safe default: status is explicit and all data-bearing foundation surfaces fail closed. */
public final class UnavailablePanelFoundationApiBackend implements PanelFoundationApiBackend {
    private final Clock clock;

    public UnavailablePanelFoundationApiBackend(Clock clock) {
        if (clock == null) throw new IllegalArgumentException("clock must not be null");
        this.clock = clock;
    }

    @Override
    public PanelIntegrationHealth status(ApiCallContext context) {
        context.requirePanelRequest();
        EnumMap<PanelSurface, PanelSurfaceStatus> surfaces = new EnumMap<>(PanelSurface.class);
        for (PanelSurface surface : PanelSurface.values()) {
            PanelSurfaceAvailability availability = surface == PanelSurface.HEALTH
                    ? PanelSurfaceAvailability.DEGRADED : PanelSurfaceAvailability.UNAVAILABLE;
            surfaces.put(surface, new PanelSurfaceStatus(surface, availability, "BACKEND_NOT_WIRED"));
        }
        return new PanelDegradedStateProjector().project(surfaces, clock.instant());
    }

    @Override
    public ApiPage<ApiResourceView> searchAudit(ApiCallContext context, String query, ApiPageRequest page) {
        throw unavailable("AUDIT_SEARCH");
    }

    @Override
    public ApiResourceView rateLimits(ApiCallContext context) {
        throw unavailable("RATE_LIMITS");
    }

    @Override
    public ApiResourceView featureFlags(ApiCallContext context) {
        throw unavailable("FEATURE_FLAGS");
    }

    private static ApiException unavailable(String surface) {
        return new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "API.PANEL_SURFACE_UNAVAILABLE",
                "Panel foundation surface is not configured",
                true,
                java.util.Map.of("surface", surface));
    }
}
