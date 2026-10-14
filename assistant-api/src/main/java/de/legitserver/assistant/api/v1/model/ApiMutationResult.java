package de.legitserver.assistant.api.v1.model;

/** Mutation response includes replay metadata from the underlying idempotency boundary. */
public record ApiMutationResult(ApiResourceView resource, boolean idempotentReplay) {
    public ApiMutationResult {
        if (resource == null) throw new IllegalArgumentException("resource must not be null");
    }
}
