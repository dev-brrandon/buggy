package de.legitserver.assistant.persistence.migration;

/** Immutable migration descriptor. */
public record SqlMigration(String version, String description, String fileName, String sha256) {}
