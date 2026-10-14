package de.legitserver.assistant.runtime.model;

import de.legitserver.assistant.requests.model.RequestRiskLevel;
import de.legitserver.assistant.security.authorization.AuthorizationRiskLevel;

/** Ordered runtime risk; may never understate the canonical capability risk. */
public enum ToolRiskLevel {
    LOW(0), MEDIUM(1), HIGH(2), CRITICAL(3);

    private final int rank;

    ToolRiskLevel(int rank) {
        this.rank = rank;
    }

    public boolean isHighRisk() {
        return rank >= HIGH.rank;
    }

    public boolean covers(AuthorizationRiskLevel risk) {
        return risk != null && rank >= risk.ordinal();
    }

    public boolean coveredBy(RequestRiskLevel risk) {
        return risk != null && risk.ordinal() >= rank;
    }
}
