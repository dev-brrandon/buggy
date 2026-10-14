package de.legitserver.assistant.persistence.transaction;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import javax.sql.DataSource;

/** Small JDBC transaction boundary with deterministic commit/rollback behavior. */
public final class JdbcTransactionRunner implements TransactionRunner {
    private final DataSource dataSource;

    public JdbcTransactionRunner(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public <T> T required(TransactionWork<T> work) {
        return execute(false, work);
    }

    @Override
    public <T> T readOnly(TransactionWork<T> work) {
        return execute(true, work);
    }

    private <T> T execute(boolean readOnly, TransactionWork<T> work) {
        Objects.requireNonNull(work, "work");
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            boolean originalReadOnly = connection.isReadOnly();
            int originalIsolation = connection.getTransactionIsolation();
            try {
                connection.setAutoCommit(false);
                connection.setReadOnly(readOnly);
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (Exception failure) {
                rollbackQuietly(connection, failure);
                if (failure instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new PersistenceOperationException("Persistence transaction failed", failure);
            } finally {
                restore(connection, originalAutoCommit, originalReadOnly, originalIsolation);
            }
        } catch (SQLException failure) {
            throw new PersistenceOperationException("Cannot acquire persistence connection", failure);
        }
    }

    private static void rollbackQuietly(Connection connection, Exception originalFailure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
        }
    }

    private static void restore(
            Connection connection, boolean autoCommit, boolean readOnly, int isolation) {
        try {
            connection.setReadOnly(readOnly);
            connection.setTransactionIsolation(isolation);
            connection.setAutoCommit(autoCommit);
        } catch (SQLException ignored) {
            // Connection is about to be closed; pool implementations validate/reset as an extra guard.
        }
    }
}
