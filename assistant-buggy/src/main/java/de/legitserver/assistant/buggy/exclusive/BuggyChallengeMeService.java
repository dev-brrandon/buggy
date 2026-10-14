package de.legitserver.assistant.buggy.exclusive;

import java.util.List;
import java.util.Objects;

/** Enforces non-sycophantic review for significant decisions. */
public final class BuggyChallengeMeService {
    public BuggyChallengeResult challenge(
            BuggyDecisionProposal proposal,
            List<String> counterarguments,
            List<String> blindSpots,
            List<String> verificationQuestions) {
        Objects.requireNonNull(proposal, "proposal");
        List<String> counters = List.copyOf(Objects.requireNonNull(counterarguments, "counterarguments"));
        List<String> blinds = List.copyOf(Objects.requireNonNull(blindSpots, "blindSpots"));
        if (proposal.significance() != BuggyDecisionSignificance.ROUTINE && counters.isEmpty() && blinds.isEmpty()) {
            throw new IllegalArgumentException("significant decisions require at least one counterargument or blind spot");
        }
        return new BuggyChallengeResult(proposal.decisionId(), counters, blinds, verificationQuestions);
    }
}
