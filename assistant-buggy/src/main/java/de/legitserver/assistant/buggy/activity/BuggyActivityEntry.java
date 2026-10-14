package de.legitserver.assistant.buggy.activity;

import de.legitserver.assistant.buggy.tasks.BuggyTaskId;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.observability.logging.TelemetrySanitizer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Append-only owner-readable activity record. Never substitutes for Foundation Audit. */
public record BuggyActivityEntry(
        UUID activityId,
        BuggyActivityType type,
        BuggyAttentionLevel attention,
        BuggyTaskId taskId,
        String automationId,
        String summary,
        Map<String, String> details,
        ActorId actorId,
        Instant occurredAt) {
    public BuggyActivityEntry {
        activityId = CoreValidation.requireNonNull(activityId, "activityId");
        type = CoreValidation.requireNonNull(type, "type");
        attention = CoreValidation.requireNonNull(attention, "attention");
        automationId = CoreValidation.optionalSafeToken(automationId, "automationId", 128);
        TelemetrySanitizer sanitizer = TelemetrySanitizer.standard();
        summary = CoreValidation.requireText(sanitizer.sanitizeValue(summary), "summary", 512);
        actorId = CoreValidation.requireNonNull(actorId, "actorId");
        occurredAt = CoreValidation.requireNonNull(occurredAt, "occurredAt");
        LinkedHashMap<String, String> safe = new LinkedHashMap<>();
        if (details != null) {
            if (details.size() > 32) throw new IllegalArgumentException("details exceeds 32 entries");
            for (Map.Entry<String, String> entry : details.entrySet()) {
                String key = CoreValidation.requireSafeToken(entry.getKey(), "detailKey", 64);
                String value = sanitizer.sanitizeMap(Map.of(key, entry.getValue())).get(key);
                safe.put(key, CoreValidation.requireText(value, "detailValue", 1024));
            }
        }
        details = Map.copyOf(safe);
    }
}
