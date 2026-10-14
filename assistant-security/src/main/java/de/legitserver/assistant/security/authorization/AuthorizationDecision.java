package de.legitserver.assistant.security.authorization;

/** Result of the complete fail-closed Assistant authorization pipeline. */
public record AuthorizationDecision(
        boolean allowed,
        AuthorizationDenialReason denialReason,
        long securityRevision,
        AuthorizationRiskLevel riskLevel) {

    public AuthorizationDecision {
        if (denialReason == null) throw new IllegalArgumentException("denialReason must not be null");
        if (securityRevision < 0) throw new IllegalArgumentException("securityRevision must be >= 0");
        if (allowed && denialReason != AuthorizationDenialReason.NONE) {
            throw new IllegalArgumentException("allowed decision must use NONE denial reason");
        }
        if (!allowed && denialReason == AuthorizationDenialReason.NONE) {
            throw new IllegalArgumentException("denied decision requires reason");
        }
    }

    public static AuthorizationDecision allow(long securityRevision, AuthorizationRiskLevel riskLevel) {
        return new AuthorizationDecision(true, AuthorizationDenialReason.NONE, securityRevision, riskLevel);
    }

    public static AuthorizationDecision deny(AuthorizationDenialReason reason, long revision, AuthorizationRiskLevel risk) {
        return new AuthorizationDecision(false, reason, revision, risk);
    }
}
