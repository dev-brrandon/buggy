package de.legitserver.assistant.requests.model;

import java.util.Objects;
import java.util.UUID;

/** Stable internal identifier for a request requiring policy-controlled approval. */
public record ApprovalRequestId(UUID value) implements Comparable<ApprovalRequestId> {
    public ApprovalRequestId {
        value = Objects.requireNonNull(value, "value");
    }

    public static ApprovalRequestId random() {
        return new ApprovalRequestId(UUID.randomUUID());
    }

    @Override
    public int compareTo(ApprovalRequestId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
