package de.legitserver.assistant.requests.repository;

import de.legitserver.assistant.requests.model.ApprovalRequestId;

/** Request aggregate was not found. */
public final class RequestNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RequestNotFoundException(ApprovalRequestId requestId) {
        super("Request not found: " + requestId);
    }
}
