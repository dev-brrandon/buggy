package de.legitserver.assistant.buggy.exclusive;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Validates evidence provenance and prevents unsupported single-source root-cause claims. */
public final class BuggyCrossSystemCorrelationService {
    private final Clock clock;

    public BuggyCrossSystemCorrelationService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BuggyCorrelationReport correlate(
            String subject,
            List<BuggyCorrelationEvidence> evidence,
            List<BuggyRootCauseHypothesis> hypotheses) {
        List<BuggyCorrelationEvidence> evidenceCopy = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        Map<String, BuggyCorrelationEvidence> byId = new HashMap<>();
        for (BuggyCorrelationEvidence item : evidenceCopy) {
            if (byId.put(item.evidenceId(), item) != null) throw new IllegalArgumentException("duplicate evidenceId");
        }
        List<BuggyRootCauseHypothesis> hypothesisCopy = List.copyOf(Objects.requireNonNull(hypotheses, "hypotheses"));
        if (hypothesisCopy.isEmpty()) {
            return new BuggyCorrelationReport(
                    BuggyCorrelationStatus.NO_RELIABLE_RESULT, subject, evidenceCopy, List.of(), clock.instant());
        }
        for (BuggyRootCauseHypothesis hypothesis : hypothesisCopy) {
            Set<BuggyEvidenceSource> sourceKinds = new HashSet<>();
            for (String evidenceId : hypothesis.supportingEvidenceIds()) {
                BuggyCorrelationEvidence item = byId.get(evidenceId);
                if (item == null) throw new IllegalArgumentException("hypothesis references unknown supporting evidence");
                sourceKinds.add(item.source());
            }
            for (String evidenceId : hypothesis.contradictingEvidenceIds()) {
                if (!byId.containsKey(evidenceId)) throw new IllegalArgumentException("hypothesis references unknown contradicting evidence");
            }
            if (sourceKinds.size() < 2) {
                throw new IllegalArgumentException("cross-system root-cause hypothesis requires at least two evidence source types");
            }
        }
        return new BuggyCorrelationReport(
                BuggyCorrelationStatus.EVIDENCE_BACKED, subject, evidenceCopy, hypothesisCopy, clock.instant());
    }
}
