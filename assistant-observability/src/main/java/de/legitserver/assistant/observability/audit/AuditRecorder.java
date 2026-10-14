package de.legitserver.assistant.observability.audit;

/** Central helper applying the Phase-6 critical-audit fail-closed rule. */
public final class AuditRecorder {
    private final AuditPort port;

    public AuditRecorder(AuditPort port) {
        if (port == null) throw new IllegalArgumentException("port must not be null");
        this.port = port;
    }

    public AuditAppendResult append(AuditRecord record) {
        if (record == null) throw new IllegalArgumentException("record must not be null");
        AuditAppendResult result;
        try {
            result = port.append(record);
        } catch (RuntimeException unavailable) {
            result = AuditAppendResult.unavailable();
        }
        if (result == null) result = AuditAppendResult.unavailable();
        if (record.critical() && !result.recorded()) {
            throw new AuditUnavailableException(result.safeCode());
        }
        return result;
    }
}
