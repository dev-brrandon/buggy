package de.legitserver.assistant.api.internal.networkcore;

import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.security.authn.ServiceAuthenticationResult;
import java.time.Instant;

public final class UnavailableNetworkCoreIngressAuthenticator implements NetworkCoreIngressAuthenticationPort {
    @Override public ServiceAuthenticationResult authenticate(String authorizationHeader, RequestId requestId, Instant now) {
        return null;
    }
}
