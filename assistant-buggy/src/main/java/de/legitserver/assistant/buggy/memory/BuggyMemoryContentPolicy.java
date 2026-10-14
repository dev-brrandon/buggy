package de.legitserver.assistant.buggy.memory;

/** G1.5 port for applying the Foundation secret boundary before memory persistence. */
public interface BuggyMemoryContentPolicy {
    String requirePersistableText(String value, String field, int maxLength);

    boolean containsSecretMaterial(String value);
}
