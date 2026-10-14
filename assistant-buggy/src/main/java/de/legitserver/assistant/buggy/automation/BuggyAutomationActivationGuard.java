package de.legitserver.assistant.buggy.automation;

@FunctionalInterface
public interface BuggyAutomationActivationGuard {
    boolean approved(BuggyAutomationRevision automation);
}
