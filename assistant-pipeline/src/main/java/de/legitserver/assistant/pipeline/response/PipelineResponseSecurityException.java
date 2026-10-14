package de.legitserver.assistant.pipeline.response;

/** Safe response-security rejection signal. */
public final class PipelineResponseSecurityException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PipelineResponseSecurityException(String safeCode) {
        super(safeCode == null ? "RESPONSE_REJECTED" : safeCode);
    }
}
