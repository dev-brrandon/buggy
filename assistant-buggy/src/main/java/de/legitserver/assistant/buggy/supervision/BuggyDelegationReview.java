package de.legitserver.assistant.buggy.supervision;

import java.util.Objects;

/** Buggy review never rewrites the target result; it only classifies the next coordination step. */
public record BuggyDelegationReview(
        BuggyDelegationResult targetResult,
        BuggyDelegationReviewStatus reviewStatus,
        boolean preservesTargetDisagreement) {
    public BuggyDelegationReview {
        targetResult = Objects.requireNonNull(targetResult, "targetResult");
        reviewStatus = Objects.requireNonNull(reviewStatus, "reviewStatus");
        if (!preservesTargetDisagreement) throw new IllegalArgumentException("target disagreement must be preserved");
    }
}
