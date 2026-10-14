package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.activity.BuggyAttentionLevel;
import de.legitserver.assistant.buggy.automation.BuggyAutomationRevision;
import de.legitserver.assistant.buggy.automation.BuggyAutomationState;
import de.legitserver.assistant.buggy.automation.BuggyAutomationTriggerType;
import de.legitserver.assistant.buggy.automation.BuggyAutomationLevel;
import de.legitserver.assistant.buggy.automation.BuggySignalType;
import de.legitserver.assistant.buggy.identity.BuggyBootstrapSeed;
import de.legitserver.assistant.buggy.service.BuggyIdentityService;
import de.legitserver.assistant.buggy.service.BuggyTaskService;
import de.legitserver.assistant.buggy.tasks.BuggyTaskStatus;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.persistence.transaction.JdbcTransactionRunner;
import de.legitserver.assistant.requests.model.ApprovalRequestId;
import de.legitserver.assistant.runtime.model.ToolId;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
import de.legitserver.assistant.security.authorization.buggy.BuggySecurityCapabilities;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** B6 live PostgreSQL append-only/task/automation persistence verification. */
public final class B6BuggyTasksAutomationPostgresSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-04T23:05:00Z");
    private static final ActorId OWNER = new ActorId("actor:buggy-b6-postgres-owner");
    private static final ActorId SYSTEM = new ActorId("system:buggy-b6-postgres-test");

    private B6BuggyTasksAutomationPostgresSelfTest() {}

    public static void main(String[] args) {
        if (args.length != 3) throw new IllegalArgumentException("Expected JDBC URL, user and password");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(args[0], args[1], args[2]);
        JdbcTransactionRunner transactions = new JdbcTransactionRunner(dataSource);
        clear(transactions);
        try {
            BuggyIdentityService identity = new BuggyIdentityService(new JdbcBuggyIdentityRepository(transactions));
            identity.initialize(BuggyBootstrapSeed.create(OWNER, SYSTEM, NOW));
            JdbcBuggyTaskRepository taskRepository = new JdbcBuggyTaskRepository(transactions);
            JdbcBuggyActivityRepository activity = new JdbcBuggyActivityRepository(transactions);
            BuggyTaskService tasks = new BuggyTaskService(taskRepository, activity, Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));
            var task = tasks.create("B6 PostgreSQL", "Verify durable tasks.", BuggyAttentionLevel.NOTICE, OWNER);
            tasks.transition(task.taskId(), BuggyTaskStatus.ACTIVE, 50, null, BuggyAttentionLevel.NOTICE, OWNER);
            require(taskRepository.history(task.taskId()).size() == 2, "task history must persist");
            require(activity.recent(10).size() == 2, "activity must persist");

            JdbcBuggyAutomationRepository automations = new JdbcBuggyAutomationRepository(transactions);
            BuggyAutomationRevision automation = new BuggyAutomationRevision(
                    "auto:b6-postgres", 1, null, "B6 PostgreSQL automation", BuggyAutomationState.ENABLED,
                    BuggyAutomationTriggerType.SIGNAL, BuggySignalType.POSTGRESQL_HEALTH, null,
                    BuggyAutomationLevel.ACT, BuggyAttentionLevel.NOTICE,
                    new ToolId("tool.diagnostics.service.health"), BuggySecurityCapabilities.DIAGNOSTICS_READ,
                    ToolRiskLevel.LOW, 3, Duration.ofSeconds(30), Duration.ofMinutes(10), 10,
                    Duration.ofMinutes(2), 3, null, OWNER, NOW.plusSeconds(2));
            automations.append(0, automation);
            require(automations.enabledForSignal(BuggySignalType.POSTGRESQL_HEALTH).size() == 1,
                    "enabled signal automation must persist");

            expectDatabaseFailure(transactions,
                    "UPDATE assistant.buggy_task_revisions SET title='tampered' WHERE assistant_id='BUGGY' AND task_id='"
                            + task.taskId().value() + "'",
                    "task history update must be rejected");
            expectDatabaseFailure(transactions,
                    "DELETE FROM assistant.buggy_activity WHERE assistant_id='BUGGY'",
                    "activity deletion must be rejected");
            expectDatabaseFailure(transactions,
                    "UPDATE assistant.buggy_automation_revisions SET name='tampered' WHERE assistant_id='BUGGY' AND automation_id='auto:b6-postgres'",
                    "automation history update must be rejected");
            System.out.println("B6_BUGGY_TASKS_AUTOMATION_POSTGRES_SELF_TEST: PASS");
        } finally {
            clear(transactions);
        }
    }

    private static void clear(JdbcTransactionRunner transactions) {
        transactions.required(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("TRUNCATE TABLE assistant.buggy_automation_runs, assistant.buggy_automation_head, "
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
