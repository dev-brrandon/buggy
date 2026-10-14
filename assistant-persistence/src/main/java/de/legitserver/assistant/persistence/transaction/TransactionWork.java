package de.legitserver.assistant.persistence.transaction;

import java.sql.Connection;

@FunctionalInterface
public interface TransactionWork<T> {
    T execute(Connection connection) throws Exception;
}
