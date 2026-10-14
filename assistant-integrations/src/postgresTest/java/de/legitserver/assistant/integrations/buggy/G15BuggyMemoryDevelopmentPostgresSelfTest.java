package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.identity.BuggyBootstrapSeed;
import de.legitserver.assistant.buggy.memory.BuggyMemoryCandidate;
import de.legitserver.assistant.buggy.memory.BuggyMemoryProvenance;
import de.legitserver.assistant.buggy.memory.BuggyMemoryRetention;
import de.legitserver.assistant.buggy.memory.BuggyMemoryRevision;
import de.legitserver.assistant.buggy.memory.BuggyMemoryType;
import de.legitserver.assistant.buggy.nativecore.BuggySelfCapabilityStatus;
import de.legitserver.assistant.buggy.service.BuggyDevelopmentService;
import de.legitserver.assistant.buggy.service.BuggyIdentityService;
import de.legitserver.assistant.buggy.service.BuggyMemoryService;
import de.legitserver.assistant.buggy.service.BuggyNativeCoreService;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.persistence.transaction.JdbcTransactionRunner;
import de.legitserver.assistant.security.secret.SecretGuard;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Live PostgreSQL 18 G1.5 verification for memory/development history, isolation and immutability. */
public final class G15BuggyMemoryDevelopmentPostgresSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-06T16:00:00Z");
    private static final ActorId OWNER = new ActorId("actor:g15-postgres-owner");
    private static final ActorId TEAM = new ActorId("actor:g15-postgres-team");
    private static final ActorId BUGGY = new ActorId("assistant:buggy");
    private static final ActorId SYSTEM = new ActorId("system:g15-postgres-test");

    private G15BuggyMemoryDevelopmentPostgresSelfTest() {}

    public static void main(String[] args) {
        if (args.length != 3) throw new IllegalArgumentException("Expected JDBC URL, user and password");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(args[0], args[1], args[2]);
        JdbcTransactionRunner transactions = new JdbcTransactionRunner(dataSource);
        clear(transactions);
        try {
            Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
            BuggyIdentityService identity = new BuggyIdentityService(new JdbcBuggyIdentityRepository(transactions));
            identity.initialize(BuggyBootstrapSeed.create(OWNER, SYSTEM, NOW.minusSeconds(120)));
            BuggyNativeCoreService nativeCore = new BuggyNativeCoreService(new JdbcBuggyNativeCoreRepository(transactions));
            nativeCore.initializeFirstActivation(NOW.minusSeconds(60));

            FoundationSecretGuardedMemoryContentPolicy policy =
                    new FoundationSecretGuardedMemoryContentPolicy(new SecretGuard());
            JdbcBuggyMemoryRepository memoryRepository = new JdbcBuggyMemoryRepository(transactions, policy);
            JdbcBuggyDevelopmentRepository developmentRepository =
                    new JdbcBuggyDevelopmentRepository(transactions, policy);
            BuggyMemoryService memory = new BuggyMemoryService(identity, memoryRepository, policy, clock);
            BuggyDevelopmentService development = new BuggyDevelopmentService(
                    nativeCore, memoryRepository, developmentRepository, policy, clock);

            BuggyMemoryCandidate ownerCandidate = memory.propose(
                    BuggyMemoryType.PRIMARY_OWNER,
                    OWNER,
                    "owner.preference.g15-postgres",
                    "Keep Native Memory append-only and Owner-isolated.",
                    BuggyMemoryProvenance.EXPLICIT,
                    "owner:postgres:g15",
                    10_000,
                    BuggyMemoryRetention.PERMANENT,
                    true,
                    OWNER);
            BuggyMemoryRevision ownerMemory = memory.acceptCandidate(
                    ownerCandidate.candidateId(), "explicit owner preference", BUGGY);
            require(memory.findForActor(ownerMemory.memoryId(), OWNER).isPresent(), "owner can read owner memory");
            expectSecurityFailure(() -> memory.findForActor(ownerMemory.memoryId(), TEAM));

            BuggyMemoryCandidate experienceCandidate = memory.propose(
                    BuggyMemoryType.EXPERIENCE,
                    null,
                    "experience.g15-postgres",
                    "Initial implementation observation.",
                    BuggyMemoryProvenance.OBSERVED,
                    "g15:postgres:observation:1",
                    8_000,
                    BuggyMemoryRetention.LONG_TERM,
                    false,
                    BUGGY);
            BuggyMemoryRevision experience = memory.acceptCandidate(
                    experienceCandidate.candidateId(), "validated observation", BUGGY);
            BuggyMemoryRevision revised = memory.supersede(
                    experience.memoryId(),
                    1,
                    "Validated implementation and PostgreSQL persistence observation.",
                    BuggyMemoryProvenance.DERIVED,
                    "g15:postgres:observation:2",
                    9_800,
                    BuggyMemoryRetention.PERMANENT,
                    true,
                    BUGGY);
            require(revised.revision() == 2, "superseding creates revision 2");
            require(memoryRepository.history(experience.memoryId()).size() == 2, "memory history persisted");

            var reflection = development.reflect(
                    List.of(ownerMemory.memoryId(), revised.memoryId()),
                    "Owner isolation and append-only memory persistence are intact.",
                    "Retain evidence refs and never rewrite prior memory revisions.",
                    9_700,
                    BUGGY);
            var learning = development.proposeLearning(
                    reflection.reflectionId(),
                    "learning.memory.append-only",
                    "Preserve old revisions and project current state through a head pointer.",
                    9_600,
                    BUGGY);
            require(developmentRepository.findLearningCandidate(learning.candidateId()).isPresent(),
                    "learning candidate persisted");

            require(development.currentCapability("memory.long-term").status() == BuggySelfCapabilityStatus.UNAVAILABLE,
                    "birth baseline remains original before current-self revision");
            development.reviseCapability(
                    "memory.long-term",
                    BuggySelfCapabilityStatus.AVAILABLE,
                    8_500,
                    "G1.5 Native Memory persistence verified on PostgreSQL 18.",
                    "g1.5:postgres:selftest",
                    BUGGY);
            require(development.currentCapability("memory.long-term").status() == BuggySelfCapabilityStatus.AVAILABLE,
                    "current self capability revision persisted");
            require(nativeCore.loadRequired().capabilities().stream()
                            .filter(item -> item.capabilityId().equals("memory.long-term"))
                            .findFirst().orElseThrow().status() == BuggySelfCapabilityStatus.UNAVAILABLE,
                    "G1.4 immutable baseline unchanged");
            require(development.journal(20).size() == 3, "development journal persisted atomically with development events");

            expectFailure(() -> memory.propose(
                    BuggyMemoryType.EXPERIENCE,
                    null,
                    "experience.secret.g15",
                    "password=do-not-store-this",
                    BuggyMemoryProvenance.EXPLICIT,
                    "g15:postgres:secret",
                    10_000,
                    BuggyMemoryRetention.LONG_TERM,
                    false,
                    OWNER));

            expectDatabaseFailure(
                    transactions,
                    "UPDATE assistant.buggy_memory_revisions SET content='tampered' WHERE assistant_id='BUGGY'",
                    "memory revision update rejected");
            expectDatabaseFailure(
                    transactions,
                    "DELETE FROM assistant.buggy_reflections WHERE assistant_id='BUGGY'",
                    "reflection delete rejected");
            expectDatabaseFailure(
                    transactions,
                    "UPDATE assistant.buggy_self_capability_revisions SET confidence_bps=1 WHERE assistant_id='BUGGY'",
                    "capability revision update rejected");
            expectDatabaseFailure(
                    transactions,
                    "DELETE FROM assistant.buggy_native_development_journal WHERE assistant_id='BUGGY'",
                    "development journal delete rejected");

            System.out.println("G1.5 BUGGY MEMORY & DEVELOPMENT POSTGRES SELF TEST: PASS");
        } finally {
            clear(transactions);
        }
    }

    private static void clear(JdbcTransactionRunner transactions) {
        transactions.required(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("TRUNCATE TABLE assistant.buggy_identity_core CASCADE");
            }
            return null;
        });
    }

    private static void expectDatabaseFailure(
            JdbcTransactionRunner transactions, String sql, String description) {
        try {
            transactions.required(connection -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate(sql);
                }
                return null;
            });
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError(description);
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError("Expected failure");
    }

    private static void expectSecurityFailure(Runnable action) {
        try {
            action.run();
        } catch (SecurityException expected) {
            return;
        }
        throw new AssertionError("Expected SecurityException");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
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

        @Override public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(jdbcUrl, user, password);
        }
        @Override public Connection getConnection(String username, String suppliedPassword) throws SQLException {
            return DriverManager.getConnection(jdbcUrl, username, suppliedPassword);
        }
        @Override public PrintWriter getLogWriter() throws SQLException { return DriverManager.getLogWriter(); }
        @Override public void setLogWriter(PrintWriter out) throws SQLException { DriverManager.setLogWriter(out); }
        @Override public void setLoginTimeout(int seconds) throws SQLException { DriverManager.setLoginTimeout(seconds); }
        @Override public int getLoginTimeout() throws SQLException { return DriverManager.getLoginTimeout(); }
        @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return Logger.getLogger("de.legitserver.assistant.integrations.buggy");
        }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) return iface.cast(this);
            throw new SQLException("Not a wrapper for " + iface.getName());
        }
        @Override public boolean isWrapperFor(Class<?> iface) { return iface.isInstance(this); }
    }
}
