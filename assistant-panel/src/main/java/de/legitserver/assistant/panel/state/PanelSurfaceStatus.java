package de.legitserver.assistant.panel.state;

import de.legitserver.assistant.core.validation.CoreValidation;

/** Safe per-surface status; detail is a bounded code, never a stacktrace. */
public record PanelSurfaceStatus(PanelSurface surface, PanelSurfaceAvailability availability, String safeCode) {
    public PanelSurfaceStatus {
        surface = CoreValidation.requireNonNull(surface, "surface");
        availability = CoreValidation.requireNonNull(availability, "availability");
        safeCode = CoreValidation.requireSafeToken(safeCode, "safeCode", 96);
    }
}
