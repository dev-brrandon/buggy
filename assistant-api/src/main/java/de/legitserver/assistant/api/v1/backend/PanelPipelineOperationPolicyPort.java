package de.legitserver.assistant.api.v1.backend;

import de.legitserver.assistant.pipeline.model.PipelineOperation;

@FunctionalInterface
public interface PanelPipelineOperationPolicyPort {
    PanelPipelineOperationPolicy policy(PipelineOperation operation);
}
