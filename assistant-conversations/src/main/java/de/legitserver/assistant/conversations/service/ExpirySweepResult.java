package de.legitserver.assistant.conversations.service;

/** Result of one bounded expiry sweep. */
public record ExpirySweepResult(int candidates, int expired, int conflicts) {
    public ExpirySweepResult {
        if (candidates < 0 || expired < 0 || conflicts < 0 || expired + conflicts > candidates) {
            throw new IllegalArgumentException("Invalid expiry sweep counters");
        }
    }
}
