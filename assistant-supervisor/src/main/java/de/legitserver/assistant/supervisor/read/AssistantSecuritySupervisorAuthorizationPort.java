package de.legitserver.assistant.supervisor.read;

import de.legitserver.assistant.core.assistant.AssistantCapabilities;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.security.authorization.AssistantAuthorizationService;
import de.legitserver.assistant.security.authorization.AuthorizationDecision;
import de.legitserver.assistant.security.authorization.AuthorizationRequest;
import de.legitserver.assistant.security.authorization.ResourceRef;
import java.util.Objects;

/** Delegates Supervisor read authorization to the canonical Phase-3 authority pipeline. */
public final class AssistantSecuritySupervisorAuthorizationPort implements SupervisorAuthorizationPort {
    private final AssistantAuthorizationService authorization;

    public AssistantSecuritySupervisorAuthorizationPort(AssistantAuthorizationService authorization) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    @Override
    public AuthorizationDecision authorize(SupervisorReadContext context, AssistantId targetAssistant) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(targetAssistant, "targetAssistant");
        return authorization.authorize(new AuthorizationRequest(
                context.servicePrincipal(),
                context.actor(),
                context.assistant(),
                AssistantCapabilities.SUPERVISOR_OBSERVE,
                new ResourceRef("assistant_supervisor", targetAssistant.name().toLowerCase(), DataClassification.INTERNAL)));
    }
}
