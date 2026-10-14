package de.legitserver.assistant.pipeline.port;

import de.legitserver.assistant.pipeline.model.PipelineRequest;
import de.legitserver.assistant.security.authn.ServiceAuthenticationResult;
import java.time.Instant;

@FunctionalInterface
public interface PipelineAuthenticationPort {
    ServiceAuthenticationResult authenticate(PipelineRequest request, Instant now);
}
