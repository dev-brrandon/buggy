package de.legitserver.assistant.api.v1.error;

import de.legitserver.assistant.api.observability.RequestCorrelationFilter;
import de.legitserver.assistant.observability.tracing.TraceCorrelation;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** One public error envelope for validation, domain, availability and unexpected failures. */
@RestControllerAdvice
public final class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorEnvelope> api(ApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).body(new ApiErrorEnvelope(
                exception.errorCode(), exception.getMessage(), requestId(request), exception.retryable(), exception.details()));
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        MissingRequestHeaderException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiErrorEnvelope> invalid(Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ApiErrorEnvelope(
                "API.INVALID_ARGUMENT", "Request validation failed", requestId(request), false, Map.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorEnvelope> unexpected(Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiErrorEnvelope(
                "API.INTERNAL", "Internal server error", requestId(request), true, Map.of()));
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestCorrelationFilter.CORRELATION_ATTRIBUTE);
        return value instanceof TraceCorrelation correlation ? correlation.requestId().value() : "unavailable";
    }
}
