package de.legitserver.assistant.pipeline.port;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.pipeline.model.PipelineRequest;

@FunctionalInterface
public interface PipelineConversationResolverPort {
    PipelineConversationResolution resolve(PipelineRequest request, ActorContext actor);
}
