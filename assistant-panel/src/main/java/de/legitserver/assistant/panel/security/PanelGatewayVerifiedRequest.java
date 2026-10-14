package de.legitserver.assistant.panel.security;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.security.authn.ServicePrincipal;

/** Trusted request metadata after both service and Panel user-context verification. */
public record PanelGatewayVerifiedRequest(ServicePrincipal servicePrincipal, ActorContext presentedActor) {
    public PanelGatewayVerifiedRequest {
        if (servicePrincipal == null || presentedActor == null) throw new IllegalArgumentException("verified request fields required");
    }
}
