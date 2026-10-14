package de.legitserver.assistant.pipeline.model;

import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** Safe response crossing the Phase-13 runtime boundary. */
public record PipelineResponse(
        RequestId requestId,
        TraceId traceId,
        PipelineStatus status,
        String safeCode,
        Map<String, Object> output,
        DataClassification classification,
        String formattedBody,
        Duration duration,
        boolean idempotentReplay) {
    public PipelineResponse {
        requestId = Objects.requireNonNull(requestId, "requestId");
        traceId = Objects.requireNonNull(traceId, "traceId");
        status = Objects.requireNonNull(status, "status");
        if (safeCode == null || safeCode.isBlank() || safeCode.length() > 96) {
            throw new IllegalArgumentException("safeCode must contain 1..96 characters");
        }
        output = output == null ? Map.of() : Map.copyOf(output);
        classification = Objects.requireNonNull(classification, "classification");
        formattedBody = formattedBody == null ? "" : formattedBody;
        duration = Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) throw new IllegalArgumentException("duration must not be negative");
    }

    public boolean successful() {
        return status == PipelineStatus.SUCCESS;
    }

    public PipelineResponse asReplay(RequestId replayRequestId, TraceId replayTraceId, Duration replayDuration) {
        return new PipelineResponse(
                Objects.requireNonNull(replayRequestId, "replayRequestId"),
                Objects.requireNonNull(replayTraceId, "replayTraceId"),
                status,
                safeCode,
                output,
                classification,
                formattedBody,
                replayDuration,
                true);
    }
}
