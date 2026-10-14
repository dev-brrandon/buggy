package de.legitserver.assistant.observability.health;

/** Initial dependency catalog from the canonical Phase-6 plan. */
public enum DependencyId {
    NETWORKCORE("NetworkCore"),
    DATABASE("Database"),
    KNOWLEDGE("Knowledge"),
    BUG_DOMAIN("Bug Domain"),
    SOURCE_REGISTRY("Source Registry"),
    DIAGNOSTICS("Diagnostics"),
    INFERENCE("Inference");

    private final String displayName;

    DependencyId(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
