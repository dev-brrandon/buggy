package de.legitserver.assistant.pipeline.port;

import de.legitserver.assistant.pipeline.model.PipelineExecutionContext;
import de.legitserver.assistant.pipeline.model.PipelineExecutionOutput;

@FunctionalInterface
public interface PipelineFormatterPort {
    String format(PipelineExecutionContext context, PipelineExecutionOutput output);
}
