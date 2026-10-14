package de.legitserver.assistant.requests.model;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.requests.validation.RequestValidation;
import java.time.Instant;
import java.util.Objects;

/** Immutable request payload revision. Approvals bind to both this revision and its exact SHA-256. */
public record RequestPayload(
        ApprovalRequestId requestId,
        long payloadRevision,
        String contentType,
        String body,
        String sha256,
        ActorId revisedBy,
        Instant createdAt) {

    public RequestPayload {
        requestId = Objects.requireNonNull(requestId, "requestId");
        if (payloadRevision < 1) throw new IllegalArgumentException("payloadRevision must be >= 1");
        contentType = RequestValidation.contentType(contentType);
        body = RequestValidation.payloadBody(body);
        sha256 = RequestValidation.sha256(sha256);
        if (!sha256.equals(RequestValidation.sha256Of(body))) {
            throw new IllegalArgumentException("sha256 does not match exact payload body");
        }
        revisedBy = Objects.requireNonNull(revisedBy, "revisedBy");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static RequestPayload create(
            ApprovalRequestId requestId,
            long payloadRevision,
            String contentType,
            String body,
            ActorId revisedBy,
            Instant createdAt) {
        String normalizedBody = RequestValidation.payloadBody(body);
        return new RequestPayload(
                requestId,
                payloadRevision,
                contentType,
                normalizedBody,
                RequestValidation.sha256Of(normalizedBody),
                revisedBy,
                createdAt);
    }
}
