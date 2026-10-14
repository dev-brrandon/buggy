package de.legitserver.assistant.core.actor;

/** Trusted transport origin assigned by server-side adapters, never by client authority. */
public enum Origin {
    PANEL,
    MINECRAFT,
    DISCORD,
    INTERNAL,
    SYSTEM
}
