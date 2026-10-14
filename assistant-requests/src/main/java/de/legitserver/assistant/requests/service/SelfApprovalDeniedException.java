package de.legitserver.assistant.requests.service;

/** HIGH/CRITICAL requester attempted to approve their own request. */
public final class SelfApprovalDeniedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public SelfApprovalDeniedException() {
        super("SELF_APPROVAL_DENIED");
    }
}
