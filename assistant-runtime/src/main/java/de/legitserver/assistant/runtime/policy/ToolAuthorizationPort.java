package de.legitserver.assistant.runtime.policy;

import de.legitserver.assistant.runtime.model.ToolDescriptor;
import de.legitserver.assistant.runtime.model.ToolInvocation;
import de.legitserver.assistant.security.authorization.AuthorizationDecision;

/** Adapter boundary into the canonical Phase-3 authorization authority pipeline. */
@FunctionalInterface
public interface ToolAuthorizationPort {
    AuthorizationDecision authorize(ToolDescriptor descriptor, ToolInvocation invocation);
}
