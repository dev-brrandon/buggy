package de.legitserver.assistant.pipeline.port;

import de.legitserver.assistant.pipeline.model.PipelineExecutionContext;
import de.legitserver.assistant.pipeline.model.PipelineExecutionOutput;

@FunctionalInterface
public interface PipelineResponseSecurityPort {
    PipelineExecutionOutput secure(PipelineExecutionContext context, PipelineExecutionOutput output);
}
