package de.legitserver.assistant.requests.model;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.requests.validation.RequestValidation;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable append-only event describing every Request state/payload/approval mutation. */
public record RequestHistoryEvent(
        UUID historyId,
        ApprovalRequestId requestId,
        RequestHistoryEventType eventType,
        ActorId actorId,
        long payloadRevision,
        String payloadSha256,
        RequestState beforeState,
        RequestState afterState,
        Map<String, String> details,
        Instant createdAt) {

    public RequestHistoryEvent {
        historyId = Objects.requireNonNull(historyId, "historyId");
        requestId = Objects.requireNonNull(requestId, "requestId");
        eventType = Objects.requireNonNull(eventType, "eventType");
        actorId = Objects.requireNonNull(actorId, "actorId");
        if (payloadRevision < 1) throw new IllegalArgumentException("payloadRevision must be >= 1");
        payloadSha256 = RequestValidation.sha256(payloadSha256);
        afterState = Objects.requireNonNull(afterState, "afterState");
        details = RequestValidation.historyDetails(details);
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static RequestHistoryEvent create(
            RequestRecord request,
            RequestHistoryEventType eventType,
            ActorId actorId,
            RequestState beforeState,
            RequestState afterState,
            Map<String, String> details,
            Instant createdAt) {
        return new RequestHistoryEvent(
                UUID.randomUUID(),
                request.requestId(),
                eventType,
                actorId,
                request.activePayloadRevision(),
                request.activePayloadSha256(),
                beforeState,
                afterState,
                details,
                createdAt);
    }
}
