package de.legitserver.assistant.requests.service;

import de.legitserver.assistant.requests.model.RequestState;

/** Requested operation is not legal in the aggregate's current state. */
public final class IllegalRequestStateException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public IllegalRequestStateException(String operation, RequestState state) {
        super(operation + " is not allowed in state " + state);
    }
}
