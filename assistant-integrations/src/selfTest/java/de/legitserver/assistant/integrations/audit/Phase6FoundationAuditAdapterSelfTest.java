package de.legitserver.assistant.integrations.audit;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.observability.audit.AuditAction;
import de.legitserver.assistant.observability.audit.AuditOutcome;
import de.legitserver.assistant.observability.audit.AuditRecord;
import de.legitserver.platform.api.model.AuditEntry;
import de.legitserver.platform.api.service.AuditService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

/** Verifies mapping to the canonical LegitPlatform AuditService contract. */
public final class Phase6FoundationAuditAdapterSelfTest {
    private Phase6FoundationAuditAdapterSelfTest() {}

    public static void main(String[] args) {
        AtomicReference<AuditEntry> captured = new AtomicReference<>();
        AuditService success = entry -> {
            captured.set(entry);
            return CompletableFuture.completedFuture(null);
        };
        FoundationAuditAdapter adapter = new FoundationAuditAdapter(success, Duration.ofMillis(250));
        AuditRecord record = record();
        require(adapter.append(record).recorded(), "canonical audit append must succeed");
        AuditEntry entry = captured.get();
        require(entry != null, "canonical audit entry must be emitted");
        require("assistant.security_change".equals(entry.action()), "canonical action mapping mismatch");
        require(entry.actorUuid().orElseThrow().equals(record.actorUuid()), "actor UUID must map");
        require("legit-assistant".equals(entry.details().get("source")), "source marker required");
        require(record.requestId().value().equals(entry.details().get("requestId")), "request correlation required");
        require(record.traceId().value().equals(entry.details().get("traceId")), "trace correlation required");
        require("[REDACTED]".equals(entry.details().get("apiToken")), "audit details must be sanitized");

        AuditService failure = new AuditService() {
            @Override
            public CompletionStage<Void> record(AuditEntry ignored) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                future.completeExceptionally(new IllegalStateException("backend detail must not escape"));
                return future;
            }
        };
        require(
                !new FoundationAuditAdapter(failure, Duration.ofMillis(250)).append(record).recorded(),
                "canonical audit failure must become safe unavailable result");
        System.out.println("PHASE6_FOUNDATION_AUDIT_ADAPTER_SELF_TEST: PASS");
    }

    private static AuditRecord record() {
        return new AuditRecord(
                AuditAction.SECURITY_CHANGE,
                AuditOutcome.SUCCESS,
                new RequestId("req-phase6-foundation-audit"),
                new TraceId("44444444444444444444444444444444"),
                new ActorId("actor:phase6"),
                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                AssistantId.BUGGY,
                "security:control-plane",
                "network-01",
                "security",
                "control-plane",
                Instant.parse("2026-10-04T00:00:00Z"),
                Map.of("apiToken", "top-secret", "change", "READ_ONLY"),
                true);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
