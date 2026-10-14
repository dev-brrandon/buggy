package de.legitserver.assistant.integrations.audit;

import de.legitserver.assistant.observability.audit.AuditAppendResult;
import de.legitserver.assistant.observability.audit.AuditPort;
import de.legitserver.assistant.observability.audit.AuditRecord;
import de.legitserver.assistant.observability.logging.TelemetrySanitizer;
import de.legitserver.platform.api.model.AuditEntry;
import de.legitserver.platform.api.service.AuditService;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Bounded adapter from Assistant audit records to the canonical LegitPlatform/Foundation AuditService. */
public final class FoundationAuditAdapter implements AuditPort {
    private static final String SOURCE = "legit-assistant";

    private final AuditService auditService;
    private final long timeoutMillis;
    private final TelemetrySanitizer sanitizer;

    public FoundationAuditAdapter(AuditService auditService, Duration timeout) {
        if (auditService == null || timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("auditService and positive timeout are required");
        }
        this.auditService = auditService;
        timeoutMillis = Math.max(1L, timeout.toMillis());
        sanitizer = TelemetrySanitizer.standard();
    }

    @Override
    public AuditAppendResult append(AuditRecord record) {
        if (record == null) return AuditAppendResult.rejected();
        Map<String, String> details = new LinkedHashMap<>(record.details());
        details.put("source", SOURCE);
        details.put("requestId", record.requestId().value());
        details.put("traceId", record.traceId().value());
        details.put("outcome", record.outcome().name());
        if (record.assistantId() != null) details.put("assistantId", record.assistantId().name());
        if (record.actorId() != null) details.put("actorId", record.actorId().value());
        if (record.objectType() != null) details.put("objectType", record.objectType());
        if (record.objectId() != null) details.put("objectId", record.objectId());
        AuditEntry entry = new AuditEntry(
                "assistant." + record.action().name().toLowerCase(java.util.Locale.ROOT),
                Optional.ofNullable(record.actorUuid()),
                record.actorId() == null ? "system" : record.actorId().value(),
                Optional.empty(),
                sanitizer.sanitizeValue(record.target()),
                record.serverId() == null ? "assistant-service" : record.serverId(),
                record.occurredAt(),
                sanitizer.sanitizeMap(details));
        try {
            var stage = auditService.record(entry);
            if (stage == null) return AuditAppendResult.unavailable();
            stage.toCompletableFuture().get(timeoutMillis, TimeUnit.MILLISECONDS);
            return AuditAppendResult.recordedResult();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return AuditAppendResult.unavailable();
        } catch (ExecutionException | TimeoutException | RuntimeException unavailable) {
            return AuditAppendResult.unavailable();
        }
    }
}
