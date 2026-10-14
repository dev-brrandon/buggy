package de.legitserver.assistant.persistence;

import de.legitserver.assistant.persistence.optimistic.OptimisticLockConflictException;
import de.legitserver.assistant.persistence.optimistic.OptimisticLocking;
import de.legitserver.assistant.persistence.transaction.JdbcTransactionRunner;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Live PostgreSQL verification. It is intentionally opt-in and never part of the offline check task. */
public final class Phase4PostgresSelfTest {
    private static final String COMMIT_KEY = "phase4.postgres.commit";
    private static final String ROLLBACK_KEY = "phase4.postgres.rollback";
    private static final String LOCK_KEY = "phase4.postgres.optimistic";

    private Phase4PostgresSelfTest() {}

    public static void main(String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected JDBC URL, user and password");
        }

        JdbcTransactionRunner transactions =
                new JdbcTransactionRunner(new DriverManagerDataSource(args[0], args[1], args[2]));
        cleanup(transactions);
        verifyCommit(transactions);
        verifyRollback(transactions);
        verifyOptimisticLocking(transactions);
        verifyFoundationSeed(transactions);
        cleanup(transactions);

        System.out.println("PHASE4_POSTGRES_SELF_TEST: PASS");
    }

    private static void verifyCommit(JdbcTransactionRunner transactions) {
        transactions.required(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO assistant.platform_metadata(metadata_key, metadata_value) VALUES (?, ?)")) {
                statement.setString(1, COMMIT_KEY);
                statement.setString(2, "committed");
                require(statement.executeUpdate() == 1, "Commit probe insert count");
            }
            return null;
        });
        require(count(transactions, COMMIT_KEY) == 1, "Committed transaction was not persisted");
    }

    private static void verifyRollback(JdbcTransactionRunner transactions) {
        boolean failureObserved = false;
        try {
            transactions.required(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO assistant.platform_metadata(metadata_key, metadata_value) VALUES (?, ?)")) {
                    statement.setString(1, ROLLBACK_KEY);
                    statement.setString(2, "must-rollback");
                    statement.executeUpdate();
                }
                throw new IllegalStateException("forced rollback");
            });
        } catch (IllegalStateException expected) {
            failureObserved = true;
        }
        require(failureObserved, "Rollback probe failure was not observed");
        require(count(transactions, ROLLBACK_KEY) == 0, "Rolled-back transaction was persisted");
    }

    private static void verifyOptimisticLocking(JdbcTransactionRunner transactions) {
        transactions.required(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO assistant.platform_metadata(metadata_key, metadata_value, revision) VALUES (?, ?, 0)")) {
                statement.setString(1, LOCK_KEY);
                statement.setString(2, "v0");
                require(statement.executeUpdate() == 1, "Optimistic probe insert count");
            }
            return null;
        });

        transactions.required(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE assistant.platform_metadata SET metadata_value=?, revision=revision+1, updated_at=CURRENT_TIMESTAMP "
                            + "WHERE metadata_key=? AND revision=?")) {
                statement.setString(1, "v1");
                statement.setString(2, LOCK_KEY);
                statement.setLong(3, 0L);
                OptimisticLocking.requireExactlyOne(
                        statement.executeUpdate(), "platform_metadata", LOCK_KEY, 0L);
            }
            return null;
        });

        boolean staleRejected = false;
        try {
            transactions.required(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE assistant.platform_metadata SET metadata_value=?, revision=revision+1 "
                                + "WHERE metadata_key=? AND revision=?")) {
                    statement.setString(1, "stale");
                    statement.setString(2, LOCK_KEY);
                    statement.setLong(3, 0L);
                    OptimisticLocking.requireExactlyOne(
                            statement.executeUpdate(), "platform_metadata", LOCK_KEY, 0L);
                }
                return null;
            });
        } catch (OptimisticLockConflictException expected) {
            staleRejected = true;
        }
        require(staleRejected, "Stale optimistic update was not rejected");
    }

    private static void verifyFoundationSeed(JdbcTransactionRunner transactions) {
        String value = transactions.readOnly(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT metadata_value FROM assistant.platform_metadata WHERE metadata_key=?")) {
                statement.setString(1, "foundation.persistence.version");
                try (ResultSet result = statement.executeQuery()) {
                    require(result.next(), "Foundation persistence seed is missing");
                    return result.getString(1);
                }
            }
        });
        require("phase4".equals(value), "Foundation persistence seed is invalid");
    }

    private static int count(JdbcTransactionRunner transactions, String key) {
        return transactions.readOnly(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT count(*) FROM assistant.platform_metadata WHERE metadata_key=?")) {
                statement.setString(1, key);
                try (ResultSet result = statement.executeQuery()) {
                    require(result.next(), "Count result missing");
                    return result.getInt(1);
                }
            }
        });
    }

    private static void cleanup(JdbcTransactionRunner transactions) {
        transactions.required(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM assistant.platform_metadata WHERE metadata_key IN (?, ?, ?)")) {
                statement.setString(1, COMMIT_KEY);
                statement.setString(2, ROLLBACK_KEY);
                statement.setString(3, LOCK_KEY);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
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
            return Logger.getLogger("de.legitserver.assistant.persistence");
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("Not a wrapper for " + iface.getName());
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
