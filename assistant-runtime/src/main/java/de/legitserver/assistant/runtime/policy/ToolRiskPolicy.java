package de.legitserver.assistant.runtime.policy;

import de.legitserver.assistant.runtime.model.ToolDescriptor;
import de.legitserver.assistant.runtime.model.ToolInvocation;
import java.time.Instant;

/** Risk gate executed after schema/security validation and before concurrency/execution. */
@FunctionalInterface
public interface ToolRiskPolicy {
    void authorize(ToolDescriptor descriptor, ToolInvocation invocation, Instant now);
}
