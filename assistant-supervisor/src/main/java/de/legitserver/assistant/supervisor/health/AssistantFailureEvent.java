package de.legitserver.assistant.supervisor.health;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.runtime.model.ToolId;
import java.time.Instant;

/** Sanitized failure projection; never carries exception messages or raw payloads. */
public record AssistantFailureEvent(
        AssistantId assistantId,
        AssistantFailureCategory category,
        String safeCode,
        AssistantCapabilityId capability,
        ToolId toolId,
        String target,
        Instant occurredAt) {
    public AssistantFailureEvent {
        assistantId = CoreValidation.requireNonNull(assistantId, "assistantId");
        category = CoreValidation.requireNonNull(category, "category");
        safeCode = CoreValidation.requireUpperCode(safeCode, "safeCode", 96);
        target = CoreValidation.requireText(target, "target", 256);
        occurredAt = CoreValidation.requireNonNull(occurredAt, "occurredAt");
    }
}
