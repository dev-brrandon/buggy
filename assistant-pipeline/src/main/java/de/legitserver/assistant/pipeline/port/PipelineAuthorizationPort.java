package de.legitserver.assistant.pipeline.port;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.pipeline.model.PipelineRequest;
import de.legitserver.assistant.security.authn.ServicePrincipal;

@FunctionalInterface
public interface PipelineAuthorizationPort {
    PipelineAuthorizationDecision authorize(PipelineRequest request, ServicePrincipal principal, ActorContext actor);
}
