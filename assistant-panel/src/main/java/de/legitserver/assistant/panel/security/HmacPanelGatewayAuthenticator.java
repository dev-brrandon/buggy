package de.legitserver.assistant.panel.security;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.actor.ActorType;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import de.legitserver.assistant.security.authn.ServiceAuthenticationResult;
import de.legitserver.assistant.security.authn.ServiceScopes;
import java.time.Instant;
import java.util.Set;

/** Verifies Panel service authentication first, then the request-bound signed user context. */
public final class HmacPanelGatewayAuthenticator implements PanelGatewayAuthenticationPort {
    private final HmacServiceTokenService serviceTokens;
    private final HmacPanelUserContextService userContexts;

    public HmacPanelGatewayAuthenticator(HmacServiceTokenService serviceTokens, HmacPanelUserContextService userContexts) {
        if (serviceTokens == null || userContexts == null) throw new IllegalArgumentException("gateway auth dependencies required");
        this.serviceTokens = serviceTokens;
        this.userContexts = userContexts;
    }

    @Override
    public PanelGatewayAuthenticationResult authenticate(
            String authorizationHeader,
            String userContextToken,
            RequestId requestId,
            TraceId traceId,
            Instant now) {
        String token = bearer(authorizationHeader);
        ServiceAuthenticationResult service = serviceTokens.verify(
                token, PanelHeaders.SERVICE_AUDIENCE, requestId, Set.of(ServiceScopes.INVOKE), now);
        if (!service.authenticated()) {
            return PanelGatewayAuthenticationResult.denied(
                    PanelGatewayAuthenticationStatus.SERVICE_AUTHENTICATION_FAILED,
                    "PANEL_SERVICE_" + service.status().name());
        }
        PanelUserContextVerification user = userContexts.verify(
                userContextToken, requestId, traceId, service.principal().credentialId(), now);
        if (!user.valid()) {
            return PanelGatewayAuthenticationResult.denied(
                    PanelGatewayAuthenticationStatus.USER_CONTEXT_FAILED,
                    "PANEL_CONTEXT_" + user.status().name());
        }
        PanelUserContextClaims claims = user.claims();
        ActorContext actor = new ActorContext(
                claims.actorId(), ActorType.HUMAN, Origin.PANEL, claims.networkIdentityId(), null,
                claims.panelUserId(), null, requestId, traceId, claims.serverId(), 0L, claims.issuedAt());
        return PanelGatewayAuthenticationResult.authenticated(
                new PanelGatewayVerifiedRequest(service.principal(), actor));
    }

    private static String bearer(String authorizationHeader) {
        if (authorizationHeader == null) return "";
        String trimmed = authorizationHeader.trim();
        if (trimmed.length() <= 7 || !trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) return "";
        return trimmed.substring(7).trim();
    }
}
