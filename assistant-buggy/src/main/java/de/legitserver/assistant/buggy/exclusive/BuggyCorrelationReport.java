package de.legitserver.assistant.buggy.exclusive;

import de.legitserver.assistant.core.validation.CoreValidation;
import java.time.Instant;
import java.util.List;

public record BuggyCorrelationReport(
        BuggyCorrelationStatus status,
        String subject,
        List<BuggyCorrelationEvidence> evidence,
        List<BuggyRootCauseHypothesis> hypotheses,
        Instant generatedAt) {
    public BuggyCorrelationReport {
        status = CoreValidation.requireNonNull(status, "status");
        subject = CoreValidation.requireText(subject, "subject", 512);
        evidence = List.copyOf(CoreValidation.requireNonNull(evidence, "evidence"));
        hypotheses = List.copyOf(CoreValidation.requireNonNull(hypotheses, "hypotheses"));
        generatedAt = CoreValidation.requireNonNull(generatedAt, "generatedAt");
        if (status == BuggyCorrelationStatus.NO_RELIABLE_RESULT && !hypotheses.isEmpty()) {
            throw new IllegalArgumentException("NO_RELIABLE_RESULT cannot carry hypotheses");
        }
        if (status == BuggyCorrelationStatus.EVIDENCE_BACKED && hypotheses.isEmpty()) {
            throw new IllegalArgumentException("EVIDENCE_BACKED requires hypotheses");
        }
    }
}
