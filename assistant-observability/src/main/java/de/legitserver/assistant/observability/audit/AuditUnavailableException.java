package de.legitserver.assistant.observability.audit;

/** Fail-closed signal used when a critical action cannot be durably audited. */
public final class AuditUnavailableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AuditUnavailableException(String safeCode) {
        super(safeCode);
    }
}
