package de.legitserver.assistant.pipeline.model;

/** Canonical Phase-13 request execution stages. */
public enum PipelineStage {
    INCOMING_REQUEST,
    AUTHENTICATION,
    ACTOR_RESOLUTION,
    AUTHORIZATION,
    CONVERSATION_RESOLUTION,
    CAPABILITY_RESOLUTION,
    TOOL_DOMAIN_ROUTING,
    POLICY,
    EXECUTION,
    RESPONSE_SECURITY,
    FORMATTING,
    AUDIT,
    RESPONSE
}
