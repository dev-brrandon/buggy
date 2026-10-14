package de.legitserver.assistant.integrations.persistence;

import de.legitserver.assistant.observability.metrics.AssistantMetric;
import de.legitserver.assistant.observability.metrics.InMemoryAssistantMetrics;
import de.legitserver.assistant.persistence.transaction.JdbcTransactionRunner;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Live PostgreSQL verification for the Phase-6 DB latency decorator. */
public final class Phase6PostgresObservabilitySelfTest {
    private Phase6PostgresObservabilitySelfTest() {}

    public static void main(String[] args) {
        if (args.length != 3) throw new IllegalArgumentException("Expected JDBC URL, user and password");
        InMemoryAssistantMetrics metrics = new InMemoryAssistantMetrics();
        var observed = new ObservedTransactionRunner(
                new JdbcTransactionRunner(new DriverManagerDataSource(args[0], args[1], args[2])),
                metrics);
        int value = observed.readOnly(connection -> {
            try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT 1")) {
                if (!result.next()) throw new IllegalStateException("SELECT 1 returned no row");
                return result.getInt(1);
            }
        });
        if (value != 1) throw new IllegalStateException("Unexpected SELECT 1 value");
        if (metrics.snapshot().latency(AssistantMetric.DATABASE_LATENCY).totalCount() != 1) {
            throw new IllegalStateException("Database latency sample missing");
        }
        System.out.println("PHASE6_POSTGRES_OBSERVABILITY_SELF_TEST: PASS");
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
        public PrintWriter getLogWriter() {
            return DriverManager.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) {
            DriverManager.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) {
            DriverManager.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() {
            return DriverManager.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
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
