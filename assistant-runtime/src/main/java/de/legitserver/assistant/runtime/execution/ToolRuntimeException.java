package de.legitserver.assistant.runtime.execution;

/** Safe tool runtime exception carrying only bounded failure classification. */
public final class ToolRuntimeException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final ToolFailureCode code;

    public ToolRuntimeException(ToolFailureCode code) {
        super(ObjectsHelper.name(code));
        this.code = ObjectsHelper.require(code);
    }

    public ToolRuntimeException(ToolFailureCode code, Throwable cause) {
        super(ObjectsHelper.name(code), cause);
        this.code = ObjectsHelper.require(code);
    }

    public ToolFailureCode code() {
        return code;
    }

    private static final class ObjectsHelper {
        private ObjectsHelper() {}
        private static ToolFailureCode require(ToolFailureCode value) {
            if (value == null) throw new IllegalArgumentException("code must not be null");
            return value;
        }
        private static String name(ToolFailureCode value) {
            return require(value).name();
        }
    }
}
