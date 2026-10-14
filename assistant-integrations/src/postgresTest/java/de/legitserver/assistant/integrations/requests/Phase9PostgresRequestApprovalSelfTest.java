package de.legitserver.assistant.integrations.requests;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.assistant.AssistantId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.request.RequestContext;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.observability.audit.AuditAppendResult;
import de.legitserver.assistant.observability.audit.AuditPort;
import de.legitserver.assistant.observability.audit.AuditRecord;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.observability.audit.AuditUnavailableException;
import de.legitserver.assistant.persistence.transaction.JdbcTransactionRunner;
import de.legitserver.assistant.requests.model.ApprovalRequestId;
import de.legitserver.assistant.requests.model.RequestDraft;
import de.legitserver.assistant.requests.model.RequestRecord;
import de.legitserver.assistant.requests.model.RequestRiskLevel;
import de.legitserver.assistant.requests.model.RequestState;
import de.legitserver.assistant.requests.policy.RequestApprovalPolicy;
import de.legitserver.assistant.requests.repository.RequestRevisionConflictException;
import de.legitserver.assistant.requests.service.IllegalRequestStateException;
import de.legitserver.assistant.requests.service.RequestExpiredException;
import de.legitserver.assistant.requests.service.RequestService;
import de.legitserver.assistant.requests.service.SelfApprovalDeniedException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Live PostgreSQL verification for Phase-9 payload binding, expiry, approvals, audit and concurrency. */
public final class Phase9PostgresRequestApprovalSelfTest {
    private static final Instant T0 = Instant.parse("2026-10-04T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(T0, ZoneOffset.UTC);
    private static final ActorId REQUESTER = new ActorId("staff.phase9.requester");
    private static final ActorId APPROVER_A = new ActorId("staff.phase9.approver-a");
    private static final ActorId APPROVER_B = new ActorId("staff.phase9.approver-b");

    private Phase9PostgresRequestApprovalSelfTest() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected JDBC URL, user and password");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(args[0], args[1], args[2]);
        JdbcTransactionRunner transactions = new JdbcTransactionRunner(dataSource);
        JdbcRequestRepository repository = new JdbcRequestRepository(transactions);
        RecordingAuditPort audit = new RecordingAuditPort();
        RequestService service = service(repository, audit, CLOCK);

        truncate(transactions);
        verifyDatabaseImmutabilityAndBinding(transactions, service, repository);
        truncate(transactions);
        verifyPayloadReplayProtection(service, repository);
        truncate(transactions);
        verifyExpiry(service, repository, audit);
        truncate(transactions);
        verifySelfApproval(service);
        truncate(transactions);
        verifyConcurrentApproval(service);
        truncate(transactions);
        verifyCriticalAuditFailClosed(repository);
        verifyFoundationSeed(transactions);
        truncate(transactions);

        require(audit.records.size() > 0, "request audit emitted records");
        System.out.println("PHASE9_POSTGRES_REQUEST_APPROVAL_SELF_TEST: PASS");
    }

    private static void verifyDatabaseImmutabilityAndBinding(
            JdbcTransactionRunner transactions,
            RequestService service,
            JdbcRequestRepository repository) {
        RequestRecord request = service.create(
                draft(RequestRiskLevel.MEDIUM, 1, "{\"action\":\"reload\"}", null),
                context(REQUESTER, "immut-create"));
        request = service.approve(request.requestId(), 1, context(APPROVER_A, "immut-approve"), "reviewed");
        require(request.state() == RequestState.APPROVED, "request approved for immutability probes");

        expectDatabaseFailure(transactions,
                "UPDATE requests.payload_revisions SET payload_body='tampered' WHERE request_id='"
                        + request.requestId() + "'::uuid",
                "payload revision update rejected");
        expectDatabaseFailure(transactions,
                "DELETE FROM requests.approvals WHERE request_id='" + request.requestId() + "'::uuid",
                "approval deletion rejected");
        expectDatabaseFailure(transactions,
                "DELETE FROM requests.history WHERE request_id='" + request.requestId() + "'::uuid",
                "history deletion rejected");
        expectDatabaseFailure(transactions,
                "UPDATE requests.records SET risk_level='LOW' WHERE request_id='" + request.requestId() + "'::uuid",
                "creation policy mutation rejected");

        RequestRecord reread = service.findById(request.requestId()).orElseThrow();
        require(reread.riskLevel() == RequestRiskLevel.MEDIUM, "request risk remained immutable");
        require(service.history(request.requestId(), 20).size() == 2, "history intact after tampering attempts");
        require(service.approvals(request.requestId(), 20).size() == 1, "approval intact after tampering attempt");

        boolean hashMismatchRejected = false;
        try {
            ApprovalRequestId id = request.requestId();
            transactions.required(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO requests.approvals(approval_id,request_id,payload_revision,payload_sha256,"
                                + "approver_actor_id,decision,created_at) VALUES (?,?,?,?,?,'APPROVE',CURRENT_TIMESTAMP)")) {
                    statement.setObject(1, UUID.randomUUID());
                    statement.setObject(2, id.value());
                    statement.setLong(3, reread.activePayloadRevision());
                    statement.setString(4, "0".repeat(64));
                    statement.setString(5, "staff.phase9.invalid-hash");
                    statement.executeUpdate();
                }
                return null;
            });
        } catch (RuntimeException expected) {
            hashMismatchRejected = true;
        }
        require(hashMismatchRejected, "DB composite FK rejected approval for wrong payload SHA-256");
        require(repository.countApproved(
                        reread.requestId(), reread.activePayloadRevision(), reread.activePayloadSha256()) == 1,
                "valid approval count unchanged");
    }

    private static void verifyPayloadReplayProtection(
            RequestService service, JdbcRequestRepository repository) {
        RequestRecord request = service.create(
                draft(RequestRiskLevel.MEDIUM, 1, "payload-A", null),
                context(REQUESTER, "replay-create"));
        String originalHash = request.activePayloadSha256();
        request = service.approve(request.requestId(), 1, context(APPROVER_A, "replay-approve-1"), "approved A");
        require(request.state() == RequestState.APPROVED, "payload A approved");

        request = service.revisePayload(
                request.requestId(), 2, "text/plain", "payload-B", context(REQUESTER, "replay-revise-b"), "B");
        require(request.state() == RequestState.PENDING && request.activePayloadRevision() == 2,
                "changed payload invalidated approval");
        request = service.revisePayload(
                request.requestId(), 3, "text/plain", "payload-A", context(REQUESTER, "replay-revise-a"), "back to A");
        require(request.activePayloadRevision() == 3 && request.activePayloadSha256().equals(originalHash),
                "same exact payload hash returned on new revision");
        require(repository.countApproved(request.requestId(), 3, originalHash) == 0,
                "old same-hash approval not replayed onto revision 3");

        ApprovalRequestId id = request.requestId();
        expectFailure(
                IllegalRequestStateException.class,
                () -> service.beginExecution(id, 4, context(REQUESTER, "replay-execute-blocked")));
        request = service.approve(id, 4, context(APPROVER_A, "replay-approve-3"), "approved revision 3");
        require(request.state() == RequestState.APPROVED && request.approvedPayloadRevision() == 3L,
                "new approval bound to revision 3");
    }

    private static void verifyExpiry(
            RequestService beforeExpiry,
            JdbcRequestRepository repository,
            RecordingAuditPort audit) {
        RequestRecord request = beforeExpiry.create(
                draft(RequestRiskLevel.CRITICAL, 1, "dangerous=true", T0.plusSeconds(30)),
                context(REQUESTER, "expiry-create"));
        RequestService afterExpiry = service(
                repository,
                audit,
                Clock.fixed(T0.plusSeconds(31), ZoneOffset.UTC));
        ApprovalRequestId id = request.requestId();
        expectFailure(
                RequestExpiredException.class,
                () -> afterExpiry.approve(id, 1, context(APPROVER_A, "expiry-approve"), "late"));
        RequestRecord expired = afterExpiry.findById(id).orElseThrow();
        require(expired.state() == RequestState.EXPIRED && expired.revision() == 2,
                "expired request transitioned and persisted");
        require(afterExpiry.history(id, 20).size() == 2, "expiry appended history");
    }

    private static void verifySelfApproval(RequestService service) {
        RequestRecord high = service.create(
                draft(RequestRiskLevel.HIGH, 1, "grant=permission", null),
                context(REQUESTER, "self-high-create"));
        expectFailure(
                SelfApprovalDeniedException.class,
                () -> service.approve(high.requestId(), 1, context(REQUESTER, "self-high-approve"), "self"));
        require(service.findById(high.requestId()).orElseThrow().revision() == 1,
                "HIGH self approval rejected without mutation");

        RequestRecord low = service.create(
                draft(RequestRiskLevel.LOW, 1, "ack=true", null),
                context(REQUESTER, "self-low-create"));
        low = service.approve(low.requestId(), 1, context(REQUESTER, "self-low-approve"), "allowed");
        require(low.state() == RequestState.APPROVED, "LOW self approval allowed");
    }

    private static void verifyConcurrentApproval(RequestService service) throws Exception {
        RequestRecord request = service.create(
                draft(RequestRiskLevel.MEDIUM, 1, "deploy=phase9", null),
                context(REQUESTER, "race-create"));
        ApprovalRequestId id = request.requestId();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> runConcurrentApproval(service, id, APPROVER_A, start, successes, conflicts)),
                    executor.submit(() -> runConcurrentApproval(service, id, APPROVER_B, start, successes, conflicts)));
            start.countDown();
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
        require(successes.get() == 1, "exactly one concurrent approval committed");
        require(conflicts.get() == 1, "exactly one concurrent approval conflicted");
        RequestRecord current = service.findById(id).orElseThrow();
        require(current.revision() == 2 && current.state() == RequestState.APPROVED,
                "concurrent approval changed head exactly once");
        require(service.approvals(id, 20).size() == 1, "only winning approval appended");
        require(service.history(id, 20).size() == 2, "only winning approval appended history");
    }

    private static void verifyCriticalAuditFailClosed(JdbcRequestRepository repository) {
        RecordingAuditPort working = new RecordingAuditPort();
        RequestService creator = service(repository, working, CLOCK);
        RequestRecord request = creator.create(
                draft(RequestRiskLevel.HIGH, 1, "security-change=true", null),
                context(REQUESTER, "audit-create"));

        AuditPort unavailable = record -> AuditAppendResult.unavailable();
        RequestService blocked = service(repository, unavailable, CLOCK);
        expectFailure(
                AuditUnavailableException.class,
                () -> blocked.approve(request.requestId(), 1, context(APPROVER_A, "audit-approve"), "reviewed"));
        RequestRecord unchanged = creator.findById(request.requestId()).orElseThrow();
        require(unchanged.revision() == 1 && unchanged.state() == RequestState.PENDING,
                "critical approval failed closed before PostgreSQL mutation");
        require(creator.approvals(request.requestId(), 20).isEmpty(), "no approval persisted when audit unavailable");
    }

    private static void verifyFoundationSeed(JdbcTransactionRunner transactions) {
        String value = transactions.readOnly(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT metadata_value FROM assistant.platform_metadata WHERE metadata_key='foundation.requests.version'")) {
                try (ResultSet result = statement.executeQuery()) {
                    require(result.next(), "Phase-9 foundation metadata seed present");
                    return result.getString(1);
                }
            }
        });
        require("phase9".equals(value), "Phase-9 foundation metadata seed value");
    }

    private static void expectDatabaseFailure(
            JdbcTransactionRunner transactions, String sql, String message) {
        boolean rejected = false;
        try {
            transactions.required(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(sql);
                }
                return null;
            });
        } catch (RuntimeException expected) {
            rejected = true;
        }
        require(rejected, message);
    }

    private static void runConcurrentApproval(
            RequestService service,
            ApprovalRequestId id,
            ActorId actor,
            CountDownLatch start,
            AtomicInteger successes,
            AtomicInteger conflicts) {
        try {
            start.await();
            service.approve(id, 1, context(actor, "race-" + actor.value()), "parallel");
            successes.incrementAndGet();
        } catch (RequestRevisionConflictException expected) {
            conflicts.incrementAndGet();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent approval interrupted", interrupted);
        }
    }

    private static RequestService service(
            JdbcRequestRepository repository, AuditPort auditPort, Clock clock) {
        return new RequestService(
                repository,
                new AuditRecorder(auditPort),
                new RequestApprovalPolicy(),
                clock);
    }

    private static RequestDraft draft(
            RequestRiskLevel risk,
            int requiredApprovals,
            String payload,
            Instant expiresAt) {
        return new RequestDraft(
                "CONTROLLED_WRITE_REQUEST",
                risk,
                requiredApprovals,
                "application/json",
                payload,
                expiresAt,
                DataClassification.INTERNAL);
    }

    private static RequestContext context(ActorId actor, String suffix) {
        return new RequestContext(
                new RequestId("req:" + suffix),
                new TraceId("trace:" + suffix),
                actor,
                Origin.INTERNAL,
                AssistantId.BYTE,
                null,
                "assistant-phase9-test",
                T0);
    }

    private static void truncate(JdbcTransactionRunner transactions) {
        transactions.required(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("TRUNCATE TABLE requests.history, requests.approvals, requests.payload_revisions, requests.records RESTART IDENTITY CASCADE");
            }
            return null;
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static <T extends Throwable> void expectFailure(Class<T> type, Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected failure: " + type.getSimpleName());
        } catch (Throwable failure) {
            if (type.isInstance(failure)) return;
            if (failure instanceof RuntimeException runtime) throw runtime;
            if (failure instanceof Error error) throw error;
            throw new IllegalStateException("Unexpected checked failure", failure);
        }
    }

    private static final class RecordingAuditPort implements AuditPort {
        private final List<AuditRecord> records = java.util.Collections.synchronizedList(new ArrayList<>());

        @Override
        public AuditAppendResult append(AuditRecord record) {
            records.add(record);
            return AuditAppendResult.recordedResult();
        }
    }

    private static final class DriverManagerDataSource implements DataSource {
        private final String jdbcUrl;
        private final String user;
        private final String password;

        private DriverManagerDataSource(String jdbcUrl, String user, String password) {
            this.jdbcUrl = jdbcUrl;
            this.user = user;
            this.password = password;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(jdbcUrl, user, password);
        }

        @Override
        public Connection getConnection(String username, String suppliedPassword) throws SQLException {
            return DriverManager.getConnection(jdbcUrl, username, suppliedPassword);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return DriverManager.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            DriverManager.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            DriverManager.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return DriverManager.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return Logger.getLogger("de.legitserver.assistant.integrations.requests");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) return iface.cast(this);
            throw new SQLException("Not a wrapper for " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
