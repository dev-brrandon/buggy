package de.legitserver.assistant.persistence;

/** Maps repository adapters to the schema they are allowed to own. */
public enum RepositoryDomain {
    ASSISTANT(PersistenceSchemas.ASSISTANT),
    BUGS(PersistenceSchemas.BUGS),
    KNOWLEDGE(PersistenceSchemas.KNOWLEDGE),
    REQUESTS(PersistenceSchemas.REQUESTS),
    AUDIT(PersistenceSchemas.AUDIT);

    private final String schema;

    RepositoryDomain(String schema) {
        this.schema = schema;
    }

    public String schema() {
        return schema;
    }
}
