package de.legitserver.assistant.buggy.activity;

/** Product-readable work history; security/compliance remains in Foundation Audit. */
public enum BuggyActivityType {
    TASK_CREATED,
    TASK_UPDATED,
    TASK_WAITING,
    TASK_COMPLETED,
    TASK_FAILED,
    OBSERVATION_RECEIVED,
    AUTOMATION_ANALYZED,
    AUTOMATION_PREPARED,
    AUTOMATION_ACTED,
    AUTOMATION_ESCALATED,
    AUTOMATION_SUPPRESSED,
    AUTOMATION_FAILED
}
