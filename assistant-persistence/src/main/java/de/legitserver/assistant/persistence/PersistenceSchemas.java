package de.legitserver.assistant.persistence;

import java.util.List;

/** Canonical PostgreSQL schema ownership boundaries. */
public final class PersistenceSchemas {
    public static final String ASSISTANT = "assistant";
    public static final String BUGS = "bugs";
    public static final String KNOWLEDGE = "knowledge";
    public static final String REQUESTS = "requests";
    public static final String AUDIT = "audit";
    public static final List<String> ALL = List.of(ASSISTANT, BUGS, KNOWLEDGE, REQUESTS, AUDIT);

    private PersistenceSchemas() {}
}
