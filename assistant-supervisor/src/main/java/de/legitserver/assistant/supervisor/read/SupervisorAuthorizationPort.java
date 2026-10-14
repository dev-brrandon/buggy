package de.legitserver.assistant.supervisor.read;

import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.security.authorization.AuthorizationDecision;

/** Security-authority boundary for Supervisor reads. */
@FunctionalInterface
public interface SupervisorAuthorizationPort {
    AuthorizationDecision authorize(SupervisorReadContext context, AssistantId targetAssistant);
}
