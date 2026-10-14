package de.legitserver.assistant.supervisor.selfhealing;

import de.legitserver.assistant.runtime.model.ToolInvocationResult;

/** Safe result of one automatic Self-Healing decision/attempt. */
public record SelfHealingResult(
        SelfHealingStatus status,
        String safeCode,
        ToolInvocationResult toolResult,
        SelfHealingEscalationReceipt escalation) {
    public SelfHealingResult {
        if (status == null || safeCode == null || safeCode.isBlank()) {
            throw new IllegalArgumentException("self-healing result fields must not be null");
        }
    }

    public static SelfHealingResult suppressed(SelfHealingSuppressionReason reason) {
        return new SelfHealingResult(SelfHealingStatus.SUPPRESSED, reason.name(), null, null);
    }
}
