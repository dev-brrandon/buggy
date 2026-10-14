package de.legitserver.assistant.observability.audit;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.observability.logging.TelemetrySanitizer;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Sanitized append-only Assistant audit record before mapping to the Foundation audit contract. */
public record AuditRecord(
        AuditAction action,
        AuditOutcome outcome,
        RequestId requestId,
        TraceId traceId,
        ActorId actorId,
        UUID actorUuid,
        AssistantId assistantId,
        String target,
        String serverId,
        String objectType,
        String objectId,
        Instant occurredAt,
        Map<String, String> details,
        boolean critical) {

    private static final TelemetrySanitizer SANITIZER = TelemetrySanitizer.standard();

    public AuditRecord {
        action = CoreValidation.requireNonNull(action, "action");
        outcome = CoreValidation.requireNonNull(outcome, "outcome");
        requestId = CoreValidation.requireNonNull(requestId, "requestId");
        traceId = CoreValidation.requireNonNull(traceId, "traceId");
        target = CoreValidation.requireText(target, "target", 256);
        serverId = CoreValidation.optionalSafeToken(serverId, "serverId", 128);
        objectType = CoreValidation.optionalSafeToken(objectType, "objectType", 96);
        objectId = CoreValidation.optionalSafeToken(objectId, "objectId", 192);
        occurredAt = CoreValidation.requireNonNull(occurredAt, "occurredAt");
        details = SANITIZER.sanitizeMap(details == null ? Map.of() : details);
    }
}
