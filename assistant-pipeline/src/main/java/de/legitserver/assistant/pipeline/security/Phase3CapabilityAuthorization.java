package de.legitserver.assistant.pipeline.security;

import de.legitserver.assistant.observability.authorization.ObservedAuthorizationService;
import de.legitserver.assistant.pipeline.model.PipelineExecutionContext;
import de.legitserver.assistant.pipeline.policy.PipelineCapabilityAuthorizationPort;
import de.legitserver.assistant.security.authorization.AuthorizationDecision;
import de.legitserver.assistant.security.authorization.AuthorizationRequest;

/** Delegates capability/resource policy to the existing observed Phase-3 authorization authority. */
public final class Phase3CapabilityAuthorization implements PipelineCapabilityAuthorizationPort {
    private final ObservedAuthorizationService authorization;

    public Phase3CapabilityAuthorization(ObservedAuthorizationService authorization) {
        if (authorization == null) throw new IllegalArgumentException("authorization must not be null");
        this.authorization = authorization;
    }

    @Override
    public AuthorizationDecision authorize(PipelineExecutionContext context) {
        return authorization.authorize(
                context.request().context(),
                new AuthorizationRequest(
                        context.servicePrincipal(),
                        context.actor(),
                        context.request().assistant(),
                        context.capabilityId(),
                        context.request().resource()));
    }
}
