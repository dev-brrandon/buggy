package de.legitserver.assistant.buggy.supervision;

import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.security.authorization.AssistantAuthorizationService;
import de.legitserver.assistant.security.authorization.AuthorizationDecision;
import de.legitserver.assistant.security.authorization.AuthorizationRequest;
import de.legitserver.assistant.security.authorization.ResourceRef;
import de.legitserver.assistant.security.authorization.buggy.BuggySecurityCapabilities;
import de.legitserver.assistant.supervisor.read.SupervisorAuthorizationPort;
import de.legitserver.assistant.supervisor.read.SupervisorReadContext;
import java.util.Objects;

/** Maps the existing Foundation Supervisor read gate onto Buggy's B3 supervisor-read capability. */
public final class BuggySupervisorAuthorizationPort implements SupervisorAuthorizationPort {
    private final AssistantAuthorizationService authorization;

    public BuggySupervisorAuthorizationPort(AssistantAuthorizationService authorization) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    @Override
    public AuthorizationDecision authorize(SupervisorReadContext context, AssistantId targetAssistant) {
        Objects.requireNonNull(context, "context");
        BuggySupervisorContractCatalog.requireFutureTarget(targetAssistant);
        return authorization.authorize(new AuthorizationRequest(
                context.servicePrincipal(),
                context.actor(),
                context.assistant(),
                BuggySecurityCapabilities.SUPERVISOR_READ,
                new ResourceRef(
                        "assistant_supervisor",
                        targetAssistant.name().toLowerCase(java.util.Locale.ROOT),
                        DataClassification.INTERNAL)));
    }
}
