package de.legitserver.assistant.buggy.supervision;

/** Authority meaning of one Buggy-to-assistant supervisor operation. */
public enum BuggySupervisorAuthorityMode {
    READ,
    COORDINATE,
    EXECUTE_BOUNDED,
    REQUEST_ONLY,
    FORBIDDEN
}
