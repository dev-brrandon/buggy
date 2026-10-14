package de.legitserver.assistant.observability.audit;

/** Append result without exposing backend exception details. */
public record AuditAppendResult(AuditAppendStatus status, String safeCode) {
    public AuditAppendResult {
        if (status == null) throw new IllegalArgumentException("status must not be null");
        if (safeCode == null || safeCode.isBlank()) throw new IllegalArgumentException("safeCode must not be blank");
        if (safeCode.length() > 64) throw new IllegalArgumentException("safeCode exceeds max length 64");
    }

    public boolean recorded() {
        return status == AuditAppendStatus.RECORDED;
    }

    public static AuditAppendResult recordedResult() {
        return new AuditAppendResult(AuditAppendStatus.RECORDED, "RECORDED");
    }

    public static AuditAppendResult unavailable() {
        return new AuditAppendResult(AuditAppendStatus.UNAVAILABLE, "AUDIT_UNAVAILABLE");
    }

    public static AuditAppendResult rejected() {
        return new AuditAppendResult(AuditAppendStatus.REJECTED, "AUDIT_REJECTED");
    }
}
