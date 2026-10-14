package de.legitserver.assistant.pipeline.port;

import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.pipeline.model.PipelineStage;

@FunctionalInterface
public interface PipelineStageObserver {
    void entered(RequestId requestId, PipelineStage stage);

    static PipelineStageObserver noOp() {
        return (requestId, stage) -> { };
    }
}
