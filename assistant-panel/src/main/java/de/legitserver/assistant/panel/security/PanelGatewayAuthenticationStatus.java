package de.legitserver.assistant.panel.security;

/** Bounded gateway authentication state exposed to the HTTP adapter. */
public enum PanelGatewayAuthenticationStatus {
    AUTHENTICATED,
    SERVICE_AUTHENTICATION_FAILED,
    USER_CONTEXT_FAILED,
    UNAVAILABLE
}
