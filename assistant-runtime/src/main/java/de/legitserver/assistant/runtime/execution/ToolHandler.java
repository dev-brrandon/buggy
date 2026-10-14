package de.legitserver.assistant.runtime.execution;

import de.legitserver.assistant.runtime.model.ToolOutput;

/** Trusted, pre-registered typed tool handler. It receives no command/shell string contract. */
@FunctionalInterface
public interface ToolHandler {
    ToolOutput execute(ToolExecutionContext context) throws Exception;
}
