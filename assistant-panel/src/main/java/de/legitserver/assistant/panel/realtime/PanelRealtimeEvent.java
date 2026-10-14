package de.legitserver.assistant.panel.realtime;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.Map;

/** Sanitized realtime notification. Payload contains identifiers/safe codes only, never raw domain content. */
public record PanelRealtimeEvent(
        long sequence,
        PanelRealtimeEventType type,
        String resourceId,
        Map<String, String> attributes,
        Instant occurredAt) {
    public PanelRealtimeEvent {
        if (sequence < 1) throw new IllegalArgumentException("sequence must be >= 1");
        type = CoreValidation.requireNonNull(type, "type");
        resourceId = CoreValidation.optionalSafeToken(resourceId, "resourceId", 192);
        occurredAt = CoreValidation.requireNonNull(occurredAt, "occurredAt");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        if (attributes.size() > 16) throw new IllegalArgumentException("event attributes exceed 16");
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            CoreValidation.requireSafeToken(entry.getKey(), "event attribute key", 64);
            if (entry.getValue() == null || entry.getValue().length() > 256 || entry.getValue().contains("\n")) {
                throw new IllegalArgumentException("event attribute value invalid");
            }
        }
    }
}
