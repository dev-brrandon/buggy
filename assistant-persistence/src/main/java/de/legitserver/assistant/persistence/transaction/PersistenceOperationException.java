package de.legitserver.assistant.persistence.transaction;

/** Infrastructure failure while executing persistence work. */
public final class PersistenceOperationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PersistenceOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
