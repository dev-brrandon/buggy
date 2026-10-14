package de.legitserver.assistant.pipeline.model;

import de.legitserver.assistant.conversations.model.Conversation;
import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.security.authn.ServicePrincipal;
import java.util.Objects;

/** Fully resolved execution context available only after pre-execution gates pass. */
public record PipelineExecutionContext(
        PipelineRequest request,
        ServicePrincipal servicePrincipal,
        ActorContext actor,
        Conversation conversation,
        AssistantCapabilityId capabilityId,
        PipelineRoute route) {
    public PipelineExecutionContext {
        request = Objects.requireNonNull(request, "request");
        servicePrincipal = Objects.requireNonNull(servicePrincipal, "servicePrincipal");
        actor = Objects.requireNonNull(actor, "actor");
        capabilityId = Objects.requireNonNull(capabilityId, "capabilityId");
        route = Objects.requireNonNull(route, "route");
    }
}
