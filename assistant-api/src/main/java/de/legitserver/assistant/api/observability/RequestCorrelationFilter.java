package de.legitserver.assistant.api.observability;

import de.legitserver.assistant.observability.logging.LogLevel;
import de.legitserver.assistant.observability.logging.SanitizedStructuredLogger;
import de.legitserver.assistant.observability.logging.StructuredLogEvent;
import de.legitserver.assistant.observability.metrics.AssistantMetric;
import de.legitserver.assistant.observability.metrics.AssistantMetrics;
import de.legitserver.assistant.observability.tracing.CorrelationContextHolder;
import de.legitserver.assistant.observability.tracing.CorrelationIds;
import de.legitserver.assistant.observability.tracing.TraceCorrelation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Establishes safe request/trace correlation at the first HTTP boundary. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RequestCorrelationFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String CORRELATION_ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".correlation";

    private final AssistantMetrics metrics;
    private final SanitizedStructuredLogger logger;

    public RequestCorrelationFilter(AssistantMetrics metrics, SanitizedStructuredLogger logger) {
        if (metrics == null || logger == null) throw new IllegalArgumentException("filter dependency must not be null");
        this.metrics = metrics;
        this.logger = logger;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        TraceCorrelation correlation = CorrelationIds.resolve(
                request.getHeader(REQUEST_ID_HEADER),
                request.getHeader(TRACE_ID_HEADER),
                request.getHeader(TRACEPARENT_HEADER));
        request.setAttribute(CORRELATION_ATTRIBUTE, correlation);
        response.setHeader(REQUEST_ID_HEADER, correlation.requestId().value());
        response.setHeader(TRACE_ID_HEADER, correlation.traceId().value());
        response.setHeader(TRACEPARENT_HEADER, correlation.traceParent());
        metrics.increment(AssistantMetric.REQUEST_COUNT);
        long started = System.nanoTime();
        boolean failed = false;
        CorrelationContextHolder.Scope scope = CorrelationContextHolder.open(correlation);
        try {
            try {
                filterChain.doFilter(request, response);
            } catch (ServletException | IOException | RuntimeException exception) {
                failed = true;
                throw exception;
            } finally {
                finish(request, response, correlation, started, failed);
            }
        } finally {
            scope.close();
        }
    }

    private void finish(
            HttpServletRequest request,
            HttpServletResponse response,
            TraceCorrelation correlation,
            long started,
            boolean failed) {
        Duration duration = Duration.ofNanos(Math.max(0L, System.nanoTime() - started));
        metrics.recordLatency(AssistantMetric.REQUEST_LATENCY, duration);
        int status = response.getStatus();
        if (failed || status >= 500) metrics.increment(AssistantMetric.REQUEST_ERRORS);
        if (status == 429) metrics.increment(AssistantMetric.RATE_LIMIT_HITS);
        logger.log(new StructuredLogEvent(
                Instant.now(),
                failed || status >= 500 ? LogLevel.ERROR : LogLevel.INFO,
                correlation.requestId(),
                correlation.traceId(),
                "http",
                "HTTP_" + request.getMethod(),
                failed ? "EXCEPTION" : "HTTP_" + status,
                duration,
                null,
                null,
                Map.of("status", Integer.toString(status))));
    }
}
