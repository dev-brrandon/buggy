package de.legitserver.assistant.integrations.buggy;

import de.legitserver.assistant.buggy.identity.BuggyBootstrapSeed;
import de.legitserver.assistant.buggy.nativecore.BuggySelfCapabilityStatus;
import de.legitserver.assistant.buggy.service.BuggyIdentityService;
import de.legitserver.assistant.buggy.service.BuggyNativeCoreService;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.persistence.transaction.JdbcTransactionRunner;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Live PostgreSQL 18 verification for the immutable G1.4 Native-Core birth/baseline. */
public final class G14BuggyNativeCorePostgresSelfTest {
    private static final Instant BIRTH = Instant.parse("2026-10-06T12:00:00Z");
    private static final ActorId OWNER = new ActorId("actor:g14-postgres-owner");
    private static final ActorId SYSTEM = new ActorId("system:g14-postgres-test");

    private G14BuggyNativeCorePostgresSelfTest() {}

    public static void main(String[] args) {
        if (args.length != 3) throw new IllegalArgumentException("Expected JDBC URL, user and password");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(args[0], args[1], args[2]);
        JdbcTransactionRunner transactions = new JdbcTransactionRunner(dataSource);
        clear(transactions);
        try {
            BuggyIdentityService identity = new BuggyIdentityService(new JdbcBuggyIdentityRepository(transactions));
            identity.initialize(BuggyBootstrapSeed.create(OWNER, SYSTEM, BIRTH.minusSeconds(60)));
            BuggyNativeCoreService nativeCore = new BuggyNativeCoreService(new JdbcBuggyNativeCoreRepository(transactions));

            var state = nativeCore.initializeFirstActivation(BIRTH);
            require(state.birth().conceptDate().equals(LocalDate.of(2026, 10, 6)), "concept date persisted");
            require(state.birth().bornAt().equals(BIRTH), "birth timestamp persisted");
            require(state.capabilities().stream()
                            .filter(item -> item.capabilityId().equals("memory.long-term"))
                            .findFirst().orElseThrow().status() == BuggySelfCapabilityStatus.UNAVAILABLE,
                    "G1.4 memory baseline remains unavailable at birth");
            require(nativeCore.loadRequired().sha256().equals(state.sha256()), "round-trip state hash stable");
            require(nativeCore.initializeFirstActivation(BIRTH).sha256().equals(state.sha256()), "initialization idempotent");
            expectFailure(() -> nativeCore.initializeFirstActivation(BIRTH.plusSeconds(1)));

            expectDatabaseFailure(
                    transactions,
                    "UPDATE assistant.buggy_native_core_seed SET concept_date=DATE '2026-10-07' WHERE assistant_id='BUGGY'",
                    "birth seed update rejected");
            expectDatabaseFailure(
                    transactions,
                    "DELETE FROM assistant.buggy_native_core_protocols WHERE assistant_id='BUGGY'",
                    "protocol delete rejected");
            expectDatabaseFailure(
                    transactions,
                    "UPDATE assistant.buggy_native_personality_baseline SET start_bps=1 WHERE assistant_id='BUGGY'",
                    "personality baseline update rejected");
            expectDatabaseFailure(
                    transactions,
                    "UPDATE assistant.buggy_native_maturity_baseline SET score_bps=1 WHERE assistant_id='BUGGY'",
                    "maturity baseline update rejected");
            expectDatabaseFailure(
                    transactions,
                    "UPDATE assistant.buggy_native_self_capabilities SET status='AVAILABLE', confidence_bps=10000 WHERE assistant_id='BUGGY' AND capability_id='memory.long-term'",
                    "self capability baseline update rejected");

            System.out.println("G1.4 BUGGY NATIVE CORE POSTGRES SELF TEST: PASS");
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

    private static void expectDatabaseFailure(JdbcTransactionRunner transactions, String sql, String description) {
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
