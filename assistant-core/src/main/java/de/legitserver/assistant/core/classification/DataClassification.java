package de.legitserver.assistant.core.classification;

/** Ordered handling classification for data flowing through the Assistant Platform. */
public enum DataClassification {
    PUBLIC(0),
    INTERNAL(1),
    CONFIDENTIAL(2),
    RESTRICTED(3),
    SECRET(4);

    private final int rank;

    DataClassification(int rank) {
        this.rank = rank;
    }

    public boolean isAtLeast(DataClassification other) {
        if (other == null) {
            throw new IllegalArgumentException("other must not be null");
        }
        return rank >= other.rank;
    }

    public static DataClassification max(DataClassification left, DataClassification right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("classifications must not be null");
        }
        return left.rank >= right.rank ? left : right;
    }
}
