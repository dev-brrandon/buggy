package de.legitserver.assistant.supervisor.read;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.security.authn.ServicePrincipal;

/** Authenticated context required for every Supervisor read. */
public record SupervisorReadContext(
        ServicePrincipal servicePrincipal,
        ActorContext actor,
        AssistantDescriptor assistant) {
    public SupervisorReadContext {
        if (servicePrincipal == null || actor == null || assistant == null) {
            throw new IllegalArgumentException("supervisor read context must not be null");
        }
    }
}
