package de.legitserver.assistant.pipeline.control;

import de.legitserver.assistant.pipeline.model.PipelineResponse;

/** Claim result for an idempotency key. */
public record IdempotencyDecision(IdempotencyStatus status, PipelineResponse replay) {
    public IdempotencyDecision {
        if (status == null) throw new IllegalArgumentException("status must not be null");
        if ((status == IdempotencyStatus.REPLAY) != (replay != null)) {
            throw new IllegalArgumentException("REPLAY status and response must agree");
        }
    }
}
