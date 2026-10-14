package de.legitserver.assistant.pipeline.policy;

/** Result of capability authorization, dependency health, and rate-limit policy. */
public record PipelinePolicyDecision(boolean allowed, String safeCode) {
    public PipelinePolicyDecision {
        if (safeCode == null || safeCode.isBlank() || safeCode.length() > 96) {
            throw new IllegalArgumentException("safeCode must contain 1..96 characters");
        }
    }

    public static PipelinePolicyDecision allow() {
        return new PipelinePolicyDecision(true, "ALLOW");
    }

    public static PipelinePolicyDecision deny(String safeCode) {
        return new PipelinePolicyDecision(false, safeCode);
    }
}
