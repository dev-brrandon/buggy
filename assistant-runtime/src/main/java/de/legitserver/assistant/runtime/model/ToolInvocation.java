package de.legitserver.assistant.runtime.model;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.requests.model.ApprovalRequestId;
import de.legitserver.assistant.security.authn.ServicePrincipal;
import de.legitserver.assistant.security.authorization.ResourceRef;
import java.util.Map;
import java.util.Objects;

/** Immutable controlled tool invocation. Capability/risk/schema come only from the registered descriptor. */
public record ToolInvocation(
        ToolId toolId,
        ServicePrincipal servicePrincipal,
        ActorContext actor,
        AssistantDescriptor assistant,
        ResourceRef resource,
        Map<String, Object> input,
        DataClassification inputClassification,
        ApprovalRequestId approvalRequestId) {
    public ToolInvocation {
        toolId = Objects.requireNonNull(toolId, "toolId");
        servicePrincipal = Objects.requireNonNull(servicePrincipal, "servicePrincipal");
        actor = Objects.requireNonNull(actor, "actor");
        assistant = Objects.requireNonNull(assistant, "assistant");
        resource = Objects.requireNonNull(resource, "resource");
        input = input == null ? Map.of() : Map.copyOf(input);
        inputClassification = Objects.requireNonNull(inputClassification, "inputClassification");
    }
}
