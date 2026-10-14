package de.legitserver.assistant.buggy.runtime.g1;

/** Safe G1 channel execution failure. Raw pipeline/provider exception messages never cross this boundary. */
public final class BuggyChannelExecutionException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final Kind kind;
    private final String safeCode;

    public BuggyChannelExecutionException(Kind kind, String safeCode) {
        super(kind == null ? "UNAVAILABLE" : kind.name());
        if (kind == null || safeCode == null || !safeCode.matches("[A-Z0-9_]{1,96}")) {
            throw new IllegalArgumentException("invalid Buggy channel failure");
        }
        this.kind = kind;
        this.safeCode = safeCode;
    }

    public Kind kind() { return kind; }
    public String safeCode() { return safeCode; }

    public enum Kind { ACCESS_DENIED, UNAVAILABLE }
}
