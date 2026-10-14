package de.legitserver.assistant.api.internal.networkcore;

import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import de.legitserver.assistant.security.authn.ServiceAuthenticationResult;
import de.legitserver.assistant.security.authn.ServiceAuthenticationStatus;
import de.legitserver.assistant.security.authn.ServiceScopes;
import java.time.Instant;
import java.util.Set;

public final class HmacNetworkCoreIngressAuthenticator implements NetworkCoreIngressAuthenticationPort {
    private final HmacServiceTokenService tokens;

    public HmacNetworkCoreIngressAuthenticator(HmacServiceTokenService tokens) {
        if (tokens == null) throw new IllegalArgumentException("tokens required");
        this.tokens = tokens;
    }

    @Override
    public ServiceAuthenticationResult authenticate(String authorizationHeader, RequestId requestId, Instant now) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return ServiceAuthenticationResult.deny(ServiceAuthenticationStatus.MALFORMED);
        }
        return tokens.verify(authorizationHeader.substring(7).trim(), "legit-assistant", requestId,
                Set.of(ServiceScopes.INVOKE), now);
    }
}
