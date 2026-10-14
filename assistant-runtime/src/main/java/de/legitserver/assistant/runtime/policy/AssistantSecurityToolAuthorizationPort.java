package de.legitserver.assistant.runtime.policy;

import de.legitserver.assistant.runtime.model.ToolDescriptor;
import de.legitserver.assistant.runtime.model.ToolInvocation;
import de.legitserver.assistant.security.authorization.AssistantAuthorizationService;
import de.legitserver.assistant.security.authorization.AuthorizationDecision;
import de.legitserver.assistant.security.authorization.AuthorizationRequest;
import java.util.Objects;

/** Tool adapter that delegates capability, actor-permission and resource policy to Phase-3 security. */
public final class AssistantSecurityToolAuthorizationPort implements ToolAuthorizationPort {
    private final AssistantAuthorizationService authorizationService;

    public AssistantSecurityToolAuthorizationPort(AssistantAuthorizationService authorizationService) {
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService");
    }

    @Override
    public AuthorizationDecision authorize(ToolDescriptor descriptor, ToolInvocation invocation) {
        return authorizationService.authorize(new AuthorizationRequest(
                invocation.servicePrincipal(),
                invocation.actor(),
                invocation.assistant(),
                descriptor.capability(),
                invocation.resource()));
    }
}
