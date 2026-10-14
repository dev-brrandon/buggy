package de.legitserver.assistant.pipeline.port;

import de.legitserver.assistant.pipeline.model.PipelineRequest;
import de.legitserver.assistant.security.authn.ServicePrincipal;

@FunctionalInterface
public interface PipelineActorResolverPort {
    PipelineActorResolution resolve(PipelineRequest request, ServicePrincipal principal);
}
