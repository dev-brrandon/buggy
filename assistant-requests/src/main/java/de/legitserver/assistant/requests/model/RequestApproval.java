package de.legitserver.assistant.requests.model;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.requests.validation.RequestValidation;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable approval/denial decision bound to one exact payload revision and SHA-256. */
public record RequestApproval(
        UUID approvalId,
        ApprovalRequestId requestId,
        long payloadRevision,
        String payloadSha256,
        ActorId approverActorId,
        ApprovalDecision decision,
        String reason,
        Instant createdAt) {

    public RequestApproval {
        approvalId = Objects.requireNonNull(approvalId, "approvalId");
        requestId = Objects.requireNonNull(requestId, "requestId");
        if (payloadRevision < 1) throw new IllegalArgumentException("payloadRevision must be >= 1");
        payloadSha256 = RequestValidation.sha256(payloadSha256);
        approverActorId = Objects.requireNonNull(approverActorId, "approverActorId");
        decision = Objects.requireNonNull(decision, "decision");
        reason = RequestValidation.reason(reason);
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static RequestApproval create(
            RequestRecord request,
            ActorId actorId,
            ApprovalDecision decision,
            String reason,
            Instant createdAt) {
        Objects.requireNonNull(request, "request");
        return new RequestApproval(
                UUID.randomUUID(),
                request.requestId(),
                request.activePayloadRevision(),
                request.activePayloadSha256(),
                actorId,
                decision,
                reason,
                createdAt);
    }
}
