package de.legitserver.assistant.security.authorization;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.security.authn.ServicePrincipal;

/** Complete server-side authorization input. */
public record AuthorizationRequest(
        ServicePrincipal servicePrincipal,
        ActorContext actor,
        AssistantDescriptor assistant,
        AssistantCapabilityId capabilityId,
        ResourceRef resource) {

    public AuthorizationRequest {
        servicePrincipal = CoreValidation.requireNonNull(servicePrincipal, "servicePrincipal");
        actor = CoreValidation.requireNonNull(actor, "actor");
        assistant = CoreValidation.requireNonNull(assistant, "assistant");
        capabilityId = CoreValidation.requireNonNull(capabilityId, "capabilityId");
    }
}
