package de.legitserver.assistant.requests.repository;

import de.legitserver.assistant.requests.model.ApprovalRequestId;

/** Optimistic concurrency conflict on the mutable Request head. */
public final class RequestRevisionConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RequestRevisionConflictException(ApprovalRequestId requestId, long expectedRevision) {
        super("Request revision conflict: " + requestId + " expectedRevision=" + expectedRevision);
    }
}
