package de.legitserver.assistant.buggy.exclusive;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compares proposed structured properties against canonical decisions; it never rewrites those decisions. */
public final class BuggyArchitectureGuardian {
    public BuggyArchitectureReview review(BuggyArchitectureProposal proposal, List<BuggyArchitectureDecision> decisions) {
        Objects.requireNonNull(proposal, "proposal");
        List<BuggyArchitectureFinding> findings = new ArrayList<>();
        for (BuggyArchitectureDecision decision : List.copyOf(Objects.requireNonNull(decisions, "decisions"))) {
            if (!decision.scope().equals(proposal.scope()) && !decision.scope().equals("network-wide")) continue;
            decision.requiredProperties().forEach((key, expected) -> {
                String actual = proposal.properties().get(key);
                if (actual == null) {
                    findings.add(new BuggyArchitectureFinding(
                            BuggyArchitectureFindingType.REQUIRED_PROPERTY_MISSING,
                            decision.decisionId(), key, expected, null, decision.rationale()));
                } else if (!expected.equals(actual)) {
                    findings.add(new BuggyArchitectureFinding(
                            BuggyArchitectureFindingType.REQUIRED_PROPERTY_MISMATCH,
                            decision.decisionId(), key, expected, actual, decision.rationale()));
                }
            });
            decision.forbiddenProperties().forEach((key, forbiddenValue) -> {
                String actual = proposal.properties().get(key);
                if (actual != null && ("*".equals(forbiddenValue) || forbiddenValue.equals(actual))) {
                    findings.add(new BuggyArchitectureFinding(
                            BuggyArchitectureFindingType.FORBIDDEN_PROPERTY_PRESENT,
                            decision.decisionId(), key, "not " + forbiddenValue, actual, decision.rationale()));
                }
            });
        }
        return new BuggyArchitectureReview(proposal.proposalId(), findings);
    }
}
