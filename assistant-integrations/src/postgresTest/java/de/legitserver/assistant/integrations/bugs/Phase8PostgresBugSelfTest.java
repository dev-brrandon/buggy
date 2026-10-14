package de.legitserver.assistant.integrations.bugs;

import de.legitserver.assistant.bugs.model.BugDraft;
import de.legitserver.assistant.bugs.model.BugPriority;
import de.legitserver.assistant.bugs.model.BugRecord;
import de.legitserver.assistant.bugs.model.BugRelationType;
import de.legitserver.assistant.bugs.model.BugSeverity;
import de.legitserver.assistant.bugs.model.BugSource;
import de.legitserver.assistant.bugs.model.BugStatus;
import de.legitserver.assistant.bugs.model.BugTargetMapping;
import de.legitserver.assistant.bugs.model.InternalBugId;
import de.legitserver.assistant.bugs.model.PublicBugId;
import de.legitserver.assistant.bugs.repository.BugRevisionConflictException;
import de.legitserver.assistant.bugs.service.BugService;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.persistence.transaction.JdbcTransactionRunner;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Live PostgreSQL verification for Phase-8 Bug immutability, history, relations, mapping and concurrency. */
public final class Phase8PostgresBugSelfTest {
    private static final ActorId ACTOR = new ActorId("staff.phase8");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-04T08:15:00Z"), ZoneOffset.UTC);

    private Phase8PostgresBugSelfTest() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected JDBC URL, user and password");
        JdbcTransactionRunner transactions = new JdbcTransactionRunner(new DriverManagerDataSource(args[0], args[1], args[2]));
        JdbcBugRepository repository = new JdbcBugRepository(transactions);
        BugService service = new BugService(repository, CLOCK);

        truncate(transactions);
        verifyOriginalReportAndHistoryImmutability(transactions, service);
        verifyRelationsHistoryAndMapping(service);
        verifyConcurrentUpdates(service);
        verifyMappingPerformance(service);
        verifyFoundationSeed(transactions);
        truncate(transactions);

        System.out.println("PHASE8_POSTGRES_BUG_SELF_TEST: PASS");
    }

    private static void verifyOriginalReportAndHistoryImmutability(
            JdbcTransactionRunner transactions, BugService service) {
        BugRecord bug = service.create(draft("IMM-0001", "Original report cannot change", BugTargetMapping.empty()), ACTOR);
        require(service.findByPublicId(PublicBugId.of("IMM-0001")).isPresent(), "public ID lookup");

        boolean reportMutationRejected = false;
        try {
            transactions.required(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE bugs.records SET original_report='tampered' WHERE internal_bug_id=?")) {
                    statement.setObject(1, bug.internalBugId().value());
                    statement.executeUpdate();
                }
                return null;
            });
        } catch (RuntimeException expected) {
            reportMutationRejected = true;
        }
        require(reportMutationRejected, "database rejected original report mutation");
        BugRecord reread = service.findByInternalId(bug.internalBugId()).orElseThrow();
        require(reread.originalReport().equals("Original report cannot change"), "original report unchanged after DB attempt");

        boolean historyMutationRejected = false;
        try {
            transactions.required(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM bugs.history WHERE internal_bug_id=?")) {
                    statement.setObject(1, bug.internalBugId().value());
                    statement.executeUpdate();
                }
                return null;
            });
        } catch (RuntimeException expected) {
            historyMutationRejected = true;
        }
        require(historyMutationRejected, "database rejected history deletion");
        require(service.history(bug.internalBugId(), 20).size() == 1, "history remained intact");
    }

    private static void verifyRelationsHistoryAndMapping(BugService service) {
        BugRecord bug = service.create(draft("BUG-1001", "Primary report", BugTargetMapping.empty()), ACTOR);
        String original = bug.originalReport();
        String originalHash = bug.originalReportSha256();

        bug = service.changeStatus(bug.internalBugId(), 1, BugStatus.TRIAGED, ACTOR, "triage");
        bug = service.changeSeverity(bug.internalBugId(), 2, BugSeverity.CRITICAL, ACTOR, "impact");
        bug = service.changePriority(bug.internalBugId(), 3, BugPriority.P0, ACTOR, "blocker");
        bug = service.updateSummary(bug.internalBugId(), 4, "Validated in test network", ACTOR, "analysis");
        BugTargetMapping mapping = new BugTargetMapping(
                "networkcore", "0.18.0-dev.23.5-hotfix.45", "build-45", "proxy", "production");
        bug = service.updateTargetMapping(bug.internalBugId(), 5, mapping, ACTOR, "mapped");
        bug = service.updateMetadata(bug.internalBugId(), 6, Map.of("reproduced", "true"), ACTOR, "metadata");
        require(bug.revision() == 7, "head revision after six mutations");
        require(bug.originalReport().equals(original), "service preserved original report");
        require(bug.originalReportSha256().equals(originalHash), "service preserved original report hash");

        BugRecord target = service.create(draft("BUG-1002", "Blocking target", BugTargetMapping.empty()), ACTOR);
        service.addRelation(bug.internalBugId(), BugRelationType.BLOCKS, target.internalBugId(), ACTOR, "blocks target");
        require(service.relations(bug.internalBugId(), 20).size() == 1, "relation visible from source");
        require(service.relations(target.internalBugId(), 20).size() == 1, "relation visible from target");
        require(service.removeRelation(
                        bug.internalBugId(), BugRelationType.BLOCKS, target.internalBugId(), ACTOR, "unblocked"),
                "relation removed");
        require(service.relations(bug.internalBugId(), 20).isEmpty(), "removed relation no longer visible");

        service.linkIncident(bug.internalBugId(), "incident-0001", "ERR-0001", ACTOR, "technical failure");
        require(service.incidentLinks(bug.internalBugId(), 20).size() == 1, "incident link stored");
        require(service.history(bug.internalBugId(), 50).size() == 10, "complete append-only Bug history");

        InternalBugId mappedBugId = bug.internalBugId();
        List<BugRecord> mapped = service.findByTargetMapping(
                new BugTargetMapping("networkcore", "0.18.0-dev.23.5-hotfix.45", "build-45", null, null), 20);
        require(mapped.stream().anyMatch(item -> item.internalBugId().equals(mappedBugId)), "component/version/build mapping lookup");
    }

    private static void verifyConcurrentUpdates(BugService service) throws Exception {
        BugRecord bug = service.create(draft("RACE-0001", "Concurrent update probe", BugTargetMapping.empty()), ACTOR);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> runConcurrentChange(service, bug.internalBugId(), BugStatus.TRIAGED, start, successes, conflicts)),
                    executor.submit(() -> runConcurrentChange(service, bug.internalBugId(), BugStatus.INVESTIGATING, start, successes, conflicts)));
            start.countDown();
            for (Future<?> future : futures) future.get();
        } finally {
            executor.shutdownNow();
        }
        require(successes.get() == 1, "exactly one concurrent update committed");
        require(conflicts.get() == 1, "exactly one concurrent update was rejected");
        BugRecord current = service.findByInternalId(bug.internalBugId()).orElseThrow();
        require(current.revision() == 2, "concurrent update advanced revision exactly once");
        require(service.history(bug.internalBugId(), 20).size() == 2, "only committed concurrent update entered history");
    }

    private static void runConcurrentChange(
            BugService service,
            InternalBugId bugId,
            BugStatus status,
            CountDownLatch start,
            AtomicInteger successes,
            AtomicInteger conflicts) {
        try {
            start.await();
            service.changeStatus(bugId, 1, status, ACTOR, "race");
            successes.incrementAndGet();
        } catch (BugRevisionConflictException expected) {
            conflicts.incrementAndGet();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent probe interrupted", interrupted);
        }
    }

    private static void verifyMappingPerformance(BugService service) {
        for (int i = 0; i < 320; i++) {
            String component = i % 8 == 0 ? "system-paper" : "component-" + (i % 16);
            String pluginVersion = i % 8 == 0 ? "1.1.0-hotfix.50" : "v" + (i % 25);
            String build = i % 8 == 0 ? "build-50" : "build-" + (i % 40);
            service.create(
                    draft(String.format("PERF-%04d", i), "Fixture " + i,
                            new BugTargetMapping(component, pluginVersion, build, "server-" + (i % 5), "test")),
                    ACTOR);
        }
        BugTargetMapping query = new BugTargetMapping("system-paper", "1.1.0-hotfix.50", "build-50", null, null);
        for (int i = 0; i < 5; i++) require(!service.findByTargetMapping(query, 100).isEmpty(), "mapping warmup result");
        List<Long> latencies = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            long start = System.nanoTime();
            List<BugRecord> result = service.findByTargetMapping(query, 100);
            latencies.add(System.nanoTime() - start);
            require(result.size() == 40, "mapping fixture result size");
        }
        Collections.sort(latencies);
        long p95Nanos = latencies.get((int) Math.ceil(latencies.size() * 0.95) - 1);
        long p95Millis = p95Nanos / 1_000_000L;
        System.out.println("PHASE8_BUG_MAPPING_P95_MS=" + p95Millis);
        require(p95Millis < 50, "Bug mapping P95 must stay below the Phase-0 50 ms single-aggregate/query budget");
    }

    private static void verifyFoundationSeed(JdbcTransactionRunner transactions) {
        String value = transactions.readOnly(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT metadata_value FROM assistant.platform_metadata WHERE metadata_key='foundation.bugs.version'")) {
                try (var result = statement.executeQuery()) {
                    require(result.next(), "foundation.bugs.version seed missing");
                    return result.getString(1);
                }
            }
        });
        require("phase8".equals(value), "foundation.bugs.version seed value");
    }

    private static BugDraft draft(String id, String report, BugTargetMapping mapping) {
        return new BugDraft(
                PublicBugId.of(id),
                BugSeverity.HIGH,
                BugPriority.P2,
                ACTOR,
                BugSource.of("ASSISTANT"),
                report,
                null,
                mapping,
                DataClassification.INTERNAL,
                Map.of("test", "phase8"));
    }

    private static void truncate(JdbcTransactionRunner transactions) {
        transactions.required(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("TRUNCATE bugs.history, bugs.relations, bugs.incident_links, bugs.records RESTART IDENTITY CASCADE");
            }
            return null;
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
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
            return Logger.getLogger("de.legitserver.assistant.integrations.bugs");
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
