package de.legitserver.assistant.panel.security;

import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import de.legitserver.assistant.security.authn.ServiceScope;
import de.legitserver.assistant.security.authn.ServiceTokenClaims;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Reference Panel Backend signer for one request; service and human context are cryptographically bound. */
public final class PanelGatewayRequestSigner {
    private final HmacServiceTokenService serviceTokens;
    private final HmacPanelUserContextService userContexts;
    private final String serviceId;
    private final Set<ServiceScope> scopes;
    private final Clock clock;
    private final Duration lifetime;

    public PanelGatewayRequestSigner(
            HmacServiceTokenService serviceTokens,
            HmacPanelUserContextService userContexts,
            String serviceId,
            Set<ServiceScope> scopes,
            Clock clock,
            Duration lifetime) {
        if (serviceTokens == null || userContexts == null || serviceId == null || serviceId.isBlank()
                || scopes == null || scopes.isEmpty() || clock == null || lifetime == null
                || lifetime.isZero() || lifetime.isNegative() || lifetime.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalArgumentException("invalid Panel gateway signer configuration");
        }
        this.serviceTokens = serviceTokens;
        this.userContexts = userContexts;
        this.serviceId = serviceId;
        this.scopes = Set.copyOf(scopes);
        this.clock = clock;
        this.lifetime = lifetime;
    }

    public PanelSignedHeaders sign(PanelRequestIdentity identity, RequestId requestId, TraceId traceId) {
        if (identity == null || requestId == null || traceId == null) throw new IllegalArgumentException("identity/correlation required");
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(lifetime);
        String serviceJti = id();
        ServiceTokenClaims serviceClaims = new ServiceTokenClaims(
                serviceId, PanelHeaders.SERVICE_AUDIENCE, scopes, issuedAt, expiresAt, serviceJti, requestId);
        String serviceToken = serviceTokens.issue(serviceClaims);
        PanelUserContextClaims userClaims = new PanelUserContextClaims(
                identity.actorId(), identity.networkIdentityId(), identity.panelUserId(), requestId, traceId,
                serviceJti, identity.serverId(), issuedAt, expiresAt, id());
        return new PanelSignedHeaders("Bearer " + serviceToken, userContexts.issue(userClaims));
    }

    private static String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
