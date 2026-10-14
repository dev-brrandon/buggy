package de.legitserver.assistant.observability.audit;

/** Safe bootstrap default until the canonical Foundation audit adapter is configured. */
public final class UnavailableAuditPort implements AuditPort {
    @Override
    public AuditAppendResult append(AuditRecord record) {
        if (record == null) return AuditAppendResult.rejected();
        return AuditAppendResult.unavailable();
    }
}
