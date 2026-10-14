package de.legitserver.assistant.buggy.exclusive;

import java.util.List;

public record BuggyArchitectureReview(String proposalId, List<BuggyArchitectureFinding> findings) {
    public BuggyArchitectureReview {
        if (proposalId == null || proposalId.isBlank()) throw new IllegalArgumentException("proposalId required");
        findings = List.copyOf(findings);
    }

    public boolean compliant() {
        return findings.isEmpty();
    }
}
