package de.legitserver.assistant.observability.logging;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Required Phase-6 structured log shape. */
public record StructuredLogEvent(
        Instant timestamp,
        LogLevel level,
        RequestId requestId,
        TraceId traceId,
        String component,
        String operation,
        String result,
        Duration duration,
        AssistantId assistantId,
        ActorId actorId,
        Map<String, String> fields) {

    public StructuredLogEvent {
        timestamp = CoreValidation.requireNonNull(timestamp, "timestamp");
        level = CoreValidation.requireNonNull(level, "level");
        requestId = CoreValidation.requireNonNull(requestId, "requestId");
        traceId = CoreValidation.requireNonNull(traceId, "traceId");
        component = CoreValidation.requireSafeToken(component, "component", 96);
        operation = CoreValidation.requireText(operation, "operation", 192);
        result = CoreValidation.requireSafeToken(result, "result", 96);
        duration = CoreValidation.requireNonNull(duration, "duration");
        if (duration.isNegative()) throw new IllegalArgumentException("duration must not be negative");
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }
}
