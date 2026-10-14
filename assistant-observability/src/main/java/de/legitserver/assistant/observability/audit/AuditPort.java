package de.legitserver.assistant.observability.audit;

/** Append-only port. Deliberately exposes no update or delete operation. */
@FunctionalInterface
public interface AuditPort {
    AuditAppendResult append(AuditRecord record);
}
