package de.legitserver.assistant.panel.security;

import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import java.time.Instant;

/** Inbound Panel Backend authentication boundary. */
public interface PanelGatewayAuthenticationPort {
    PanelGatewayAuthenticationResult authenticate(
            String authorizationHeader,
            String userContextToken,
            RequestId requestId,
            TraceId traceId,
            Instant now);
}
