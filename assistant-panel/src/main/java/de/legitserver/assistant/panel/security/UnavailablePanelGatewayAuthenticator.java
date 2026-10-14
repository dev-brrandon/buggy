package de.legitserver.assistant.panel.security;

import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import java.time.Instant;

/** Secure bootstrap default: Panel API remains unavailable until gateway credentials are configured. */
public final class UnavailablePanelGatewayAuthenticator implements PanelGatewayAuthenticationPort {
    @Override
    public PanelGatewayAuthenticationResult authenticate(
            String authorizationHeader, String userContextToken, RequestId requestId, TraceId traceId, Instant now) {
        return PanelGatewayAuthenticationResult.denied(PanelGatewayAuthenticationStatus.UNAVAILABLE, "PANEL_SECURITY_UNCONFIGURED");
    }
}
