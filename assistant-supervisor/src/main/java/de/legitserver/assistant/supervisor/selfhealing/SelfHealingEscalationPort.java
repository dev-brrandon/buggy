package de.legitserver.assistant.supervisor.selfhealing;

/** Narrow Phase-11 escalation contract. Product-specific report/incident integration remains future work. */
@FunctionalInterface
public interface SelfHealingEscalationPort {
    SelfHealingEscalationReceipt escalate(SelfHealingEscalation escalation);
}
