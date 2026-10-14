package de.legitserver.assistant.pipeline.port;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.pipeline.model.PipelineOperation;
import de.legitserver.assistant.pipeline.model.PipelineRoute;
import java.util.Optional;

@FunctionalInterface
public interface PipelineRouterPort {
    Optional<PipelineRoute> route(PipelineOperation operation, AssistantCapabilityId capabilityId);
}
