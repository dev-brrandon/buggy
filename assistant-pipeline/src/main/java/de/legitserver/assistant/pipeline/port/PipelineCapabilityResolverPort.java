package de.legitserver.assistant.pipeline.port;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.pipeline.model.PipelineOperation;
import java.util.Optional;

@FunctionalInterface
public interface PipelineCapabilityResolverPort {
    Optional<AssistantCapabilityId> resolve(PipelineOperation operation);
}
