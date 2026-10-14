package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.identity.BuggyBootstrapSeed;
import de.legitserver.assistant.buggy.identity.BuggyIdentitySnapshot;
import de.legitserver.assistant.buggy.runtime.BuggyIdentityRuntime;
import de.legitserver.assistant.buggy.service.BuggyIdentityService;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.persistence.transaction.JdbcTransactionRunner;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Live PostgreSQL B1 regression. Run only against the dedicated assistant test database. */
public final class B1BuggyIdentityPostgresSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-04T20:00:00Z");
    private static final ActorId OWNER = new ActorId("actor:buggy-b1-postgres-test-owner");
    private static final ActorId PROVISIONER = new ActorId("system:buggy-b1-postgres-test");

    private B1BuggyIdentityPostgresSelfTest() {}

    public static void main(String[] args) {
        if (args.length != 3) throw new IllegalArgumentException("Expected JDBC URL, user and password");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(args[0], args[1], args[2]);
        JdbcTransactionRunner transactions = new JdbcTransactionRunner(dataSource);
        JdbcBuggyIdentityRepository repository = new JdbcBuggyIdentityRepository(transactions);
        BuggyIdentityService service = new BuggyIdentityService(repository);

        clearDedicatedTestState(transactions);
        try {
            BuggyBootstrapSeed seed = BuggyBootstrapSeed.create(OWNER, PROVISIONER, NOW);
            BuggyIdentitySnapshot initialized = service.initialize(seed);
            BuggyIdentitySnapshot loaded = service.loadRequired();
            require(initialized.equals(loaded), "identity persisted and reread exactly");
            require(loaded.ownerBinding().ownerActorId().equals(OWNER), "NetworkCore owner ActorId persisted");
            require(loaded.ownerBinding().authoritySource().equals("NETWORKCORE"), "NetworkCore remains owner authority");

            BuggyIdentitySnapshot idempotent = service.initialize(seed);
            require(idempotent.equals(loaded), "same seed is idempotent");
            expectFailure(() -> service.initialize(BuggyBootstrapSeed.create(
                    new ActorId("actor:different-owner"), PROVISIONER, NOW)));

            expectDatabaseFailure(
                    transactions,
                    "UPDATE assistant.buggy_identity_core SET display_name='Mutated' WHERE assistant_id='BUGGY'",
                    "Identity Core update rejected");
            expectDatabaseFailure(
                    transactions,
                    "DELETE FROM assistant.buggy_owner_binding_revisions WHERE assistant_id='BUGGY'",
                    "Owner history deletion rejected");
            expectDatabaseFailure(
                    transactions,
                    "UPDATE assistant.buggy_config_revisions SET display_name='Mutated' WHERE assistant_id='BUGGY'",
                    "configuration history update rejected");

            Clock clock = Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC);
            BuggyIdentityRuntime runtimeA = new BuggyIdentityRuntime(service, clock);
            BuggyIdentityRuntime runtimeB = new BuggyIdentityRuntime(service, clock);
            require(runtimeA.identity().equals(runtimeB.identity()), "runtime restart preserves persistent identity");
            require(!runtimeA.runtimeInstance().runtimeInstanceId().equals(runtimeB.runtimeInstance().runtimeInstanceId()),
                    "runtime instance IDs remain ephemeral");

            require(service.loadRequired().equals(loaded), "tampering probes left canonical state intact");
            System.out.println("B1_BUGGY_IDENTITY_POSTGRES_SELF_TEST: PASS");
        } finally {
            clearDedicatedTestState(transactions);
        }
    }

    private static void clearDedicatedTestState(JdbcTransactionRunner transactions) {
        transactions.required(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        "TRUNCATE TABLE assistant.buggy_identity_head, assistant.buggy_config_revisions, "
                                + "assistant.buggy_owner_binding_revisions, assistant.buggy_identity_core CASCADE");
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
