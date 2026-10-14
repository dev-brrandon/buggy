package de.legitserver.assistant.api.v1.error;

import java.io.Serial;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Safe transport exception. Public messages/details must contain no secrets. */
public final class ApiException extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String errorCode;
    private final boolean retryable;
    private final transient Map<String, String> details;

    public ApiException(HttpStatus status, String errorCode, String publicMessage, boolean retryable) {
        this(status, errorCode, publicMessage, retryable, Map.of());
    }

    public ApiException(
            HttpStatus status,
            String errorCode,
            String publicMessage,
            boolean retryable,
            Map<String, String> details) {
        super(publicMessage);
        this.status = status;
        this.errorCode = errorCode;
        this.retryable = retryable;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public HttpStatus status() { return status; }
    public String errorCode() { return errorCode; }
    public boolean retryable() { return retryable; }
    public Map<String, String> details() { return details; }
}
