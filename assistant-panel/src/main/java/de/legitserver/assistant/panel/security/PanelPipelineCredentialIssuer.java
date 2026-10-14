package de.legitserver.assistant.panel.security;

import de.legitserver.assistant.core.request.RequestId;

/** Mints a fresh internal service credential after the external Panel request was verified. */
@FunctionalInterface
public interface PanelPipelineCredentialIssuer {
    String issue(RequestId requestId);
}
