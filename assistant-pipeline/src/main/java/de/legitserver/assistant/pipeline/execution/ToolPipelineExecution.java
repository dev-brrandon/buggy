package de.legitserver.assistant.pipeline.execution;

import de.legitserver.assistant.pipeline.model.PipelineExecutionContext;
import de.legitserver.assistant.pipeline.model.PipelineExecutionOutput;
import de.legitserver.assistant.pipeline.port.PipelineExecutionPort;
import de.legitserver.assistant.runtime.execution.ToolRuntime;
import de.legitserver.assistant.runtime.model.ToolId;
import de.legitserver.assistant.runtime.model.ToolInvocation;
import de.legitserver.assistant.runtime.model.ToolInvocationResult;
import java.util.Objects;

/** Phase-13 route adapter that enters the existing controlled Phase-10 Tool Runtime. */
public final class ToolPipelineExecution implements PipelineExecutionPort {
    private final ToolRuntime runtime;
    private final ToolId toolId;

    public ToolPipelineExecution(ToolRuntime runtime, ToolId toolId) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.toolId = Objects.requireNonNull(toolId, "toolId");
    }

    @Override
    public PipelineExecutionOutput execute(PipelineExecutionContext context) {
        ToolInvocationResult result = runtime.invoke(new ToolInvocation(
                toolId,
                context.servicePrincipal(),
                context.actor(),
                context.request().assistant(),
                context.request().resource(),
                context.request().input(),
                context.request().inputClassification(),
                context.request().approvalRequestId()));
        return new PipelineExecutionOutput(result.output(), result.classification());
    }
}
