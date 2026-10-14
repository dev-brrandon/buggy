package de.legitserver.assistant.supervisor.selfhealing;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.runtime.model.ToolId;
import java.time.Instant;

/** Sanitized escalation payload; no raw tool input or exception message is included. */
public record SelfHealingEscalation(
        AssistantId assistantId,
        ToolId toolId,
        AssistantCapabilityId capability,
        String targetFingerprint,
        int consecutiveFailures,
        String safeCode,
        Instant occurredAt) {
    public SelfHealingEscalation {
        if (assistantId == null || toolId == null || capability == null || targetFingerprint == null
                || targetFingerprint.isBlank() || safeCode == null || safeCode.isBlank() || occurredAt == null) {
            throw new IllegalArgumentException("escalation fields must not be null");
        }
        if (consecutiveFailures < 1) throw new IllegalArgumentException("consecutiveFailures must be positive");
    }
}
