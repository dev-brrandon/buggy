package de.legitserver.assistant.panel.security;

import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import de.legitserver.assistant.security.authn.ServiceScopes;
import de.legitserver.assistant.security.authn.ServiceTokenClaims;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Internal Panel-API-to-Pipeline credential issuer. External Panel credentials are never replayed internally. */
public final class HmacPanelPipelineCredentialIssuer implements PanelPipelineCredentialIssuer {
    private final HmacServiceTokenService tokens;
    private final Clock clock;
    private final Duration lifetime;

    public HmacPanelPipelineCredentialIssuer(HmacServiceTokenService tokens, Clock clock, Duration lifetime) {
        if (tokens == null || clock == null || lifetime == null || lifetime.isZero() || lifetime.isNegative()
                || lifetime.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalArgumentException("invalid internal pipeline credential issuer configuration");
        }
        this.tokens = tokens;
        this.clock = clock;
        this.lifetime = lifetime;
    }

    @Override
    public String issue(RequestId requestId) {
        if (requestId == null) throw new IllegalArgumentException("requestId must not be null");
        Instant now = clock.instant();
        return tokens.issue(new ServiceTokenClaims(
                "assistant-panel-api",
                "legit-assistant",
                Set.of(ServiceScopes.INVOKE),
                now.minusSeconds(1),
                now.plus(lifetime),
                UUID.randomUUID().toString().replace("-", ""),
                requestId));
    }
}
