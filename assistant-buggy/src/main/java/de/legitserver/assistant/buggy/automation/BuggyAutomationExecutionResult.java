package de.legitserver.assistant.buggy.automation;

import de.legitserver.assistant.core.validation.CoreValidation;

public record BuggyAutomationExecutionResult(boolean successful, String safeCode) {
    public BuggyAutomationExecutionResult {
        safeCode = CoreValidation.requireUpperCode(safeCode, "safeCode", 96);
    }
    public static BuggyAutomationExecutionResult success(String code) { return new BuggyAutomationExecutionResult(true, code); }
    public static BuggyAutomationExecutionResult failure(String code) { return new BuggyAutomationExecutionResult(false, code); }
}
