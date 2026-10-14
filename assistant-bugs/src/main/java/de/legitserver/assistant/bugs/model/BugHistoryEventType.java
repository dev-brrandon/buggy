package de.legitserver.assistant.bugs.model;

/** Append-only event catalog for complete Bug aggregate history. */
public enum BugHistoryEventType {
    CREATED,
    STATUS_CHANGED,
    SEVERITY_CHANGED,
    PRIORITY_CHANGED,
    SUMMARY_CHANGED,
    TARGET_MAPPING_CHANGED,
    METADATA_CHANGED,
    RELATION_ADDED,
    RELATION_REMOVED,
    INCIDENT_LINKED
}
