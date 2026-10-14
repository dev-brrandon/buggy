package de.legitserver.assistant.panel.security;

/** Atomically generated Panel Backend authentication headers. */
public record PanelSignedHeaders(String authorization, String userContext) {
    public PanelSignedHeaders {
        if (authorization == null || authorization.isBlank() || userContext == null || userContext.isBlank()) {
            throw new IllegalArgumentException("signed headers must not be blank");
        }
    }
}
