package de.legitserver.assistant.buggy.automation;

/** Bounded integration port. Implementations route through existing Foundation Tool/Request/Pipeline services. */
@FunctionalInterface
public interface BuggyAutomationExecutionPort {
    BuggyAutomationExecutionResult execute(BuggyAutomationRevision automation, BuggyAutomationSignal signal, BuggyAutomationLevel level);
}
