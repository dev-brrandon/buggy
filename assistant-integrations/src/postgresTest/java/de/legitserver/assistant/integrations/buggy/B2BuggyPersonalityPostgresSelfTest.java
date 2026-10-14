package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.identity.BuggyBootstrapSeed;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityDomain;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityEvidenceRef;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityEvidenceType;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityObservation;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityObservationDraft;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityObservationOrigin;
import de.legitserver.assistant.buggy.personality.BuggyPersonalitySeed;
import de.legitserver.assistant.buggy.personality.BuggyPersonalitySnapshot;
import de.legitserver.assistant.buggy.service.BuggyIdentityService;
import de.legitserver.assistant.buggy.service.BuggyPersonalityService;
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

/** Live PostgreSQL B2 regression. Run only against the dedicated assistant test database. */
public final class B2BuggyPersonalityPostgresSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-04T21:30:00Z");
    private static final ActorId OWNER = new ActorId("actor:buggy-b2-postgres-test-owner");
    private static final ActorId SYSTEM = new ActorId("system:buggy-b2-postgres-test");

    private B2BuggyPersonalityPostgresSelfTest() {}

    public static void main(String[] args) {
        if (args.length != 3) throw new IllegalArgumentException("Expected JDBC URL, user and password");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(args[0], args[1], args[2]);
        JdbcTransactionRunner transactions = new JdbcTransactionRunner(dataSource);
        FoundationSecretGuardedPersonalityContentPolicy contentPolicy =
                new FoundationSecretGuardedPersonalityContentPolicy(new SecretGuard());
        JdbcBuggyIdentityRepository identityRepository = new JdbcBuggyIdentityRepository(transactions);
        BuggyIdentityService identity = new BuggyIdentityService(identityRepository);
        JdbcBuggyPersonalityRepository personalityRepository =
                new JdbcBuggyPersonalityRepository(transactions, contentPolicy);
        BuggyPersonalityService personality = new BuggyPersonalityService(
                identity,
                personalityRepository,
                contentPolicy,
                Clock.fixed(NOW.plusSeconds(10), ZoneOffset.UTC));

        clearDedicatedTestState(transactions);
        try {
            BuggyBootstrapSeed identitySeed = BuggyBootstrapSeed.create(OWNER, SYSTEM, NOW);
            identity.initialize(identitySeed);
            BuggyPersonalitySnapshot seeded = personality.initialize(SYSTEM);
            require(seeded.current().revision() == 1, "seed revision persisted");
            require(identity.loadRequired().configuration().manifest().personalityProfileId()
                            .equals(BuggyPersonalitySeed.PROFILE_ID),
                    "personality profile activated through config revision");

            BuggyPersonalityObservation observation = personality.recordObservation(new BuggyPersonalityObservationDraft(
                    BuggyPersonalityDomain.OWNER_PREFERENCE,
                    "technical_disagreement",
                    "prefer direct technical disagreement over diplomatic agreement",
                    BuggyPersonalityObservationOrigin.EXPLICIT_OWNER,
                    9_500,
                    List.of(new BuggyPersonalityEvidenceRef(
                            BuggyPersonalityEvidenceType.OWNER_STATEMENT_REF,
                            "owner-statement-postgres-001",
                            NOW)),
                    "Explicit Owner preference",
                    OWNER));
            BuggyPersonalitySnapshot evolved = personality.promoteObservations(
                    List.of(observation.observationId()), SYSTEM, "Promote explicit Owner preference");
            require(evolved.current().revision() == 2, "evolution revision persisted");

            BuggyPersonalitySnapshot rolledBack = personality.rollback(1L, OWNER, "Rollback regression probe");
            require(rolledBack.current().revision() == 3, "rollback appended new revision");
            require(personality.historyView().revisions().size() == 3, "full revision history persisted");
            require(personality.historyView().observations().size() == 1, "observation journal persisted");

            expectFailure(() -> personality.recordObservation(new BuggyPersonalityObservationDraft(
                    BuggyPersonalityDomain.INTERACTION_HABIT,
                    "debug_notes",
                    "password=must-never-persist",
                    BuggyPersonalityObservationOrigin.SELF_REVIEW,
                    9_000,
                    List.of(new BuggyPersonalityEvidenceRef(
                            BuggyPersonalityEvidenceType.SELF_REVIEW_REF,
                            "self-review-postgres-001",
                            NOW)),
                    "secret rejection probe",
                    SYSTEM)));

            expectDatabaseFailure(
                    transactions,
                    "UPDATE assistant.buggy_personality_revisions SET reason='mutated' WHERE assistant_id='BUGGY' AND personality_revision=1",
                    "Personality revision update rejected");
            expectDatabaseFailure(
                    transactions,
                    "DELETE FROM assistant.buggy_personality_observations WHERE assistant_id='BUGGY'",
                    "Personality observation deletion rejected");
            expectDatabaseFailure(
                    transactions,
                    "UPDATE assistant.buggy_identity_core SET display_name='Mutated' WHERE assistant_id='BUGGY'",
                    "Identity Core remains protected after B2");

            require(identity.initialize(identitySeed).identityCore().canonicalId().equals("buggy"),
                    "B1 bootstrap remains idempotent after B2 config activation");
            System.out.println("B2_BUGGY_PERSONALITY_POSTGRES_SELF_TEST: PASS");
        } finally {
            clearDedicatedTestState(transactions);
        }
    }

    private static void clearDedicatedTestState(JdbcTransactionRunner transactions) {
        transactions.required(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        "TRUNCATE TABLE "
                                + "assistant.buggy_personality_head, "
                                + "assistant.buggy_personality_state_entries, "
                                + "assistant.buggy_personality_revisions, "
                                + "assistant.buggy_personality_observation_evidence, "
                                + "assistant.buggy_personality_observations, "
                                + "assistant.buggy_personality_seed_entries, "
                                + "assistant.buggy_personality_seed, "
                                + "assistant.buggy_identity_head, "
                                + "assistant.buggy_config_revisions, "
                                + "assistant.buggy_owner_binding_revisions, "
                                + "assistant.buggy_identity_core CASCADE");
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

    private static void expectFailure(Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError("Expected operation failure");
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
            return Logger.getLogger("de.legitserver.assistant.integrations.buggy");
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
