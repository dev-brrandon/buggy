package de.legitserver.assistant.requests.model;

/** Risk classification used by approval policy. */
public enum RequestRiskLevel {
    LOW(0),
    MEDIUM(1),
    HIGH(2),
    CRITICAL(3);

    private final int rank;

    RequestRiskLevel(int rank) {
        this.rank = rank;
    }

    public boolean isHighRisk() {
        return rank >= HIGH.rank;
    }

    public boolean isCritical() {
        return this == CRITICAL;
    }
}
