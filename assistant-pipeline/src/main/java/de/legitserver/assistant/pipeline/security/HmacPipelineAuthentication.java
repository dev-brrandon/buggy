package de.legitserver.assistant.pipeline.security;

import de.legitserver.assistant.pipeline.model.PipelineRequest;
import de.legitserver.assistant.pipeline.port.PipelineAuthenticationPort;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import de.legitserver.assistant.security.authn.ServiceAuthenticationResult;
import de.legitserver.assistant.security.authn.ServiceScopes;
import java.time.Instant;
import java.util.Set;

/** Request-bound service authentication adapter reusing the Phase-3 HMAC verifier. */
public final class HmacPipelineAuthentication implements PipelineAuthenticationPort {
    private final HmacServiceTokenService tokens;

    public HmacPipelineAuthentication(HmacServiceTokenService tokens) {
        if (tokens == null) throw new IllegalArgumentException("tokens must not be null");
        this.tokens = tokens;
    }

    @Override
    public ServiceAuthenticationResult authenticate(PipelineRequest request, Instant now) {
        return tokens.verify(
                request.serviceCredential(),
                "legit-assistant",
                request.context().requestId(),
                Set.of(ServiceScopes.INVOKE),
                now);
    }
}
