package de.legitserver.assistant.requests.service;

/** Mutation requiring a live request was attempted after expiry. */
public final class RequestExpiredException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RequestExpiredException() {
        super("REQUEST_EXPIRED");
    }
}
