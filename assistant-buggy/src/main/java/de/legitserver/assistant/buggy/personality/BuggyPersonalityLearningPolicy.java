package de.legitserver.assistant.buggy.personality;

import java.util.HashSet;
import java.util.Set;

/** B2 learning boundary: observations may be broad, but state promotion is deliberately conservative. */
public final class BuggyPersonalityLearningPolicy {
    private static final int MIN_GENERAL_PROMOTION_CONFIDENCE_BPS = 6_000;
    private static final int MIN_EXPLICIT_OWNER_CONFIDENCE_BPS = 7_000;
    private static final int MIN_OWNER_KNOWLEDGE_CONFIDENCE_BPS = 7_500;
    private static final int MIN_REPEATED_PATTERN_CONFIDENCE_BPS = 8_000;
    private static final int MIN_REPEATED_PATTERN_EVIDENCE = 3;

    public void validateObservation(BuggyPersonalityObservation observation) {
        if (observation.domain() != BuggyPersonalityDomain.OWNER_PREFERENCE) return;
        switch (observation.origin()) {
            case EXPLICIT_OWNER, REPEATED_OWNER_PATTERN, OWNER_KNOWLEDGE_REFERENCE -> {
                // Allowed Owner-preference evidence classes.
            }
            default -> throw new IllegalArgumentException(
                    "Owner preferences may only originate from Owner evidence or Owner Knowledge references");
        }
    }

    public void requirePromotable(BuggyPersonalityObservation observation) {
        validateObservation(observation);
        if (observation.confidenceBps() < MIN_GENERAL_PROMOTION_CONFIDENCE_BPS) {
            throw new IllegalArgumentException("Personality observation confidence is too low for state promotion");
        }
        if (observation.domain() != BuggyPersonalityDomain.OWNER_PREFERENCE) return;

        switch (observation.origin()) {
            case EXPLICIT_OWNER -> requireConfidence(observation, MIN_EXPLICIT_OWNER_CONFIDENCE_BPS);
            case OWNER_KNOWLEDGE_REFERENCE -> requireConfidence(observation, MIN_OWNER_KNOWLEDGE_CONFIDENCE_BPS);
            case REPEATED_OWNER_PATTERN -> {
                requireConfidence(observation, MIN_REPEATED_PATTERN_CONFIDENCE_BPS);
                Set<String> distinct = new HashSet<>();
                for (BuggyPersonalityEvidenceRef evidence : observation.evidence()) {
                    distinct.add(evidence.type().name() + ':' + evidence.referenceId());
                }
                if (distinct.size() < MIN_REPEATED_PATTERN_EVIDENCE) {
                    throw new IllegalArgumentException(
                            "Repeated Owner preference requires at least three distinct evidence references");
                }
            }
            default -> throw new IllegalStateException("Owner preference origin was not validated");
        }
    }

    private static void requireConfidence(BuggyPersonalityObservation observation, int minimum) {
        if (observation.confidenceBps() < minimum) {
            throw new IllegalArgumentException("Owner preference confidence is below promotion threshold");
        }
    }
}
