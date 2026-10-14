package de.legitserver.assistant.panel.security;

/** User-context verification result; claims are present only for VALID. */
public record PanelUserContextVerification(PanelUserContextStatus status, PanelUserContextClaims claims) {
    public PanelUserContextVerification {
        if (status == null) throw new IllegalArgumentException("status must not be null");
        if ((status == PanelUserContextStatus.VALID) != (claims != null)) {
            throw new IllegalArgumentException("claims presence must match VALID status");
        }
    }

    public boolean valid() {
        return status == PanelUserContextStatus.VALID;
    }

    public static PanelUserContextVerification valid(PanelUserContextClaims claims) {
        return new PanelUserContextVerification(PanelUserContextStatus.VALID, claims);
    }

    public static PanelUserContextVerification denied(PanelUserContextStatus status) {
        if (status == PanelUserContextStatus.VALID) throw new IllegalArgumentException("VALID requires claims");
        return new PanelUserContextVerification(status, null);
    }
}
