package de.legitserver.assistant.runtime.schema;

/** Safe schema validation failure. */
public final class ToolSchemaViolationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String safeCode;

    public ToolSchemaViolationException(String safeCode) {
        super(safeCode);
        this.safeCode = safeCode;
    }

    public String safeCode() {
        return safeCode;
    }
}
