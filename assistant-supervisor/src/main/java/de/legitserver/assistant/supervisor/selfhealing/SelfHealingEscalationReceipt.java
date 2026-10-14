package de.legitserver.assistant.supervisor.selfhealing;

/** Report/Incident references returned by the escalation boundary. */
public record SelfHealingEscalationReceipt(String reportId, String incidentId) {
    public SelfHealingEscalationReceipt {
        if ((reportId == null || reportId.isBlank()) && (incidentId == null || incidentId.isBlank())) {
            throw new IllegalArgumentException("escalation must create a report or incident reference");
        }
    }
}
