package de.legitserver.assistant.pipeline.policy;

import de.legitserver.assistant.pipeline.model.PipelineExecutionContext;
import de.legitserver.assistant.security.authorization.AuthorizationDecision;

@FunctionalInterface
public interface PipelineCapabilityAuthorizationPort {
    AuthorizationDecision authorize(PipelineExecutionContext context);
}
