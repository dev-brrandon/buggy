package de.legitserver.assistant.pipeline.port;

/** Safe ingress-level Assistant access authorization result. */
public record PipelineAuthorizationDecision(boolean allowed, String safeCode, long securityRevision) {
    public PipelineAuthorizationDecision {
        if (safeCode == null || safeCode.isBlank() || safeCode.length() > 96) {
            throw new IllegalArgumentException("safeCode must contain 1..96 characters");
        }
        if (securityRevision < 0) throw new IllegalArgumentException("securityRevision must be >= 0");
    }

    public static PipelineAuthorizationDecision allow(long revision) {
        return new PipelineAuthorizationDecision(true, "ALLOW", revision);
    }

    public static PipelineAuthorizationDecision deny(String safeCode, long revision) {
        return new PipelineAuthorizationDecision(false, safeCode, revision);
    }
}
