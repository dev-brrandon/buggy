package de.legitserver.assistant.panel.security;

/** Gateway authentication result with safe code only; credentials are never echoed. */
public record PanelGatewayAuthenticationResult(
        PanelGatewayAuthenticationStatus status,
        String safeCode,
        PanelGatewayVerifiedRequest verified) {
    public PanelGatewayAuthenticationResult {
        if (status == null || safeCode == null || safeCode.isBlank()) throw new IllegalArgumentException("status and safeCode required");
        if ((status == PanelGatewayAuthenticationStatus.AUTHENTICATED) != (verified != null)) {
            throw new IllegalArgumentException("verified presence must match AUTHENTICATED");
        }
    }

    public boolean authenticated() { return status == PanelGatewayAuthenticationStatus.AUTHENTICATED; }

    public static PanelGatewayAuthenticationResult authenticated(PanelGatewayVerifiedRequest verified) {
        return new PanelGatewayAuthenticationResult(PanelGatewayAuthenticationStatus.AUTHENTICATED, "AUTHENTICATED", verified);
    }

    public static PanelGatewayAuthenticationResult denied(PanelGatewayAuthenticationStatus status, String safeCode) {
        if (status == PanelGatewayAuthenticationStatus.AUTHENTICATED) throw new IllegalArgumentException("use authenticated factory");
        return new PanelGatewayAuthenticationResult(status, safeCode, null);
    }
}
