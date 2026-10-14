package de.legitserver.assistant.pipeline.routing;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.pipeline.model.PipelineOperation;
import de.legitserver.assistant.pipeline.port.PipelineCapabilityResolverPort;
import java.util.Map;
import java.util.Optional;

/** Immutable server-side operation-to-capability mapping. */
public final class StaticPipelineCapabilityResolver implements PipelineCapabilityResolverPort {
    private final Map<PipelineOperation, AssistantCapabilityId> mappings;

    public StaticPipelineCapabilityResolver(Map<PipelineOperation, AssistantCapabilityId> mappings) {
        if (mappings == null) throw new IllegalArgumentException("mappings must not be null");
        this.mappings = Map.copyOf(mappings);
    }

    @Override
    public Optional<AssistantCapabilityId> resolve(PipelineOperation operation) {
        if (operation == null) return Optional.empty();
        return Optional.ofNullable(mappings.get(operation));
    }
}
