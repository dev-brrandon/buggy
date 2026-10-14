package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.identity.BuggyBootstrapSeed;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentChangeType;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentEvidence;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentEvidenceType;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentId;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentRevision;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentState;
import de.legitserver.assistant.buggy.service.BuggyIdentityService;
import de.legitserver.assistant.contracts.source.SourceSnapshot;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.persistence.transaction.JdbcTransactionRunner;
import de.legitserver.assistant.runtime.model.ToolId;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
import de.legitserver.assistant.security.secret.SecretGuard;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** B7 live PostgreSQL append-only Self-Development persistence verification. */
public final class B7BuggySelfDevelopmentPostgresSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-05T00:25:00Z");
    private static final ActorId OWNER = new ActorId("actor:buggy-b7-postgres-owner");
    private static final ActorId SYSTEM = new ActorId("system:buggy-b7-postgres-test");
    private static final String H1 = "1111111111111111111111111111111111111111111111111111111111111111";
    private static final String H2 = "2222222222222222222222222222222222222222222222222222222222222222";

    private B7BuggySelfDevelopmentPostgresSelfTest() {}

    public static void main(String[] args) {
        if (args.length != 3) throw new IllegalArgumentException("Expected JDBC URL, user and password");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(args[0], args[1], args[2]);
        JdbcTransactionRunner transactions = new JdbcTransactionRunner(dataSource);
        clear(transactions);
        try {
            new BuggyIdentityService(new JdbcBuggyIdentityRepository(transactions))
                    .initialize(BuggyBootstrapSeed.create(OWNER, SYSTEM, NOW));
            JdbcBuggySelfDevelopmentRepository repository = new JdbcBuggySelfDevelopmentRepository(
                    transactions, new FoundationSecretGuardedSelfDevelopmentContentPolicy(new SecretGuard()));
            BuggySelfDevelopmentId id = BuggySelfDevelopmentId.random();
            SourceSnapshot source = new SourceSnapshot("assistant-buggy", "0.1.0-dev.18", "build-b7", "rev-b7", NOW, H1);
            BuggySelfDevelopmentRevision observed = new BuggySelfDevelopmentRevision(
                    id, 1, null, BuggySelfDevelopmentState.OBSERVED,
                    "B7 PostgreSQL", "Verify append-only self-development.", null, null, source,
                    null, null, null, null, null, "observed", OWNER, NOW.plusSeconds(1));
            repository.append(0, observed);
            BuggySelfDevelopmentRevision proposal = new BuggySelfDevelopmentRevision(
                    id, 2, 1L, BuggySelfDevelopmentState.PROPOSAL,
                    observed.title(), observed.problem(), "Apply a bounded source patch.",
                    BuggySelfDevelopmentChangeType.SOURCE_PATCH, source,
                    null, null, null, ToolRiskLevel.MEDIUM, null, "proposal", OWNER, NOW.plusSeconds(2));
            repository.append(1, proposal);
            repository.appendEvidence(new BuggySelfDevelopmentEvidence(
                    UUID.randomUUID(), id, 2, BuggySelfDevelopmentEvidenceType.SELF_REVIEW,
                    null, "review:b7-postgres", H2, true, OWNER, NOW.plusSeconds(3)));

            require(repository.history(id).size() == 2, "revision history must persist");
            require(repository.evidence(id).size() == 1, "evidence must persist");
            expectDatabaseFailure(transactions,
                    "UPDATE assistant.buggy_selfdev_revisions SET title='tampered' WHERE assistant_id='BUGGY' AND item_id='"
                            + id.value() + "'",
                    "Self-Development revision update must be rejected");
            expectDatabaseFailure(transactions,
                    "DELETE FROM assistant.buggy_selfdev_evidence WHERE assistant_id='BUGGY' AND item_id='" + id.value() + "'",
                    "Self-Development evidence deletion must be rejected");
            System.out.println("B7_BUGGY_SELF_DEVELOPMENT_POSTGRES_SELF_TEST: PASS");
        } finally {
            clear(transactions);
        }
    }

    private static void clear(JdbcTransactionRunner transactions) {
        transactions.required(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("TRUNCATE TABLE assistant.buggy_selfdev_evidence, assistant.buggy_selfdev_items, "
                        + "assistant.buggy_selfdev_revisions, assistant.buggy_automation_runs, assistant.buggy_automation_head, "
                        + "assistant.buggy_automation_revisions, assistant.buggy_activity, assistant.buggy_task_relations, "
                        + "assistant.buggy_task_head, assistant.buggy_task_revisions, assistant.buggy_knowledge_annotation_head, "
                        + "assistant.buggy_knowledge_annotation_revisions, assistant.buggy_personality_head, "
                        + "assistant.buggy_personality_state_entries, assistant.buggy_personality_revision_evidence, "
                        + "assistant.buggy_personality_revision_changes, assistant.buggy_personality_revisions, "
                        + "assistant.buggy_personality_observation_evidence, assistant.buggy_personality_observations, "
                        + "assistant.buggy_personality_seed_entries, assistant.buggy_personality_seed, "
                        + "assistant.buggy_identity_head, assistant.buggy_config_revisions, "
                        + "assistant.buggy_owner_binding_revisions, assistant.buggy_identity_core CASCADE");
            }
            return null;
        });
    }

    private static void expectDatabaseFailure(JdbcTransactionRunner transactions, String sql, String description) {
        try {
            transactions.required(connection -> {
                try (Statement statement = connection.createStatement()) { statement.executeUpdate(sql); }
                return null;
            });
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError(description);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class DriverManagerDataSource implements DataSource {
        private final String jdbcUrl;
        private final String user;
        private final String password;
        private DriverManagerDataSource(String jdbcUrl, String user, String password) {
            this.jdbcUrl = jdbcUrl; this.user = user; this.password = password;
        }
        @Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(jdbcUrl, user, password); }
        @Override public Connection getConnection(String username, String suppliedPassword) throws SQLException { return DriverManager.getConnection(jdbcUrl, username, suppliedPassword); }
        @Override public PrintWriter getLogWriter() throws SQLException { return DriverManager.getLogWriter(); }
        @Override public void setLogWriter(PrintWriter out) throws SQLException { DriverManager.setLogWriter(out); }
        @Override public void setLoginTimeout(int seconds) throws SQLException { DriverManager.setLoginTimeout(seconds); }
        @Override public int getLoginTimeout() throws SQLException { return DriverManager.getLoginTimeout(); }
        @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { return Logger.getLogger("de.legitserver.assistant.integrations.buggy"); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { if (iface.isInstance(this)) return iface.cast(this); throw new SQLException("Not a wrapper for " + iface.getName()); }
        @Override public boolean isWrapperFor(Class<?> iface) { return iface.isInstance(this); }
    }
}
