package de.legitserver.assistant.persistence.transaction;

public interface TransactionRunner {
    <T> T required(TransactionWork<T> work);

    <T> T readOnly(TransactionWork<T> work);
}
