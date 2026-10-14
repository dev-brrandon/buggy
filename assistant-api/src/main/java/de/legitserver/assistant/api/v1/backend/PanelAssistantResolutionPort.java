package de.legitserver.assistant.api.v1.backend;

import de.legitserver.assistant.api.v1.model.ApiCallContext;
import de.legitserver.assistant.api.v1.model.ApiCommandRequest;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.pipeline.model.PipelineOperation;

/** Server-side Assistant resolution for a Panel API mutation. Client payload cannot mint descriptors. */
@FunctionalInterface
public interface PanelAssistantResolutionPort {
    AssistantDescriptor resolve(ApiCallContext context, PipelineOperation operation, String resourceId, ApiCommandRequest request);
}
