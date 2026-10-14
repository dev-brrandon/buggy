package de.legitserver.assistant.observability.tracing;

import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;

/** Request/trace/span correlation values suitable for W3C traceparent propagation. */
public record TraceCorrelation(RequestId requestId, TraceId traceId, String spanId, String traceParent) {
    public TraceCorrelation {
        if (requestId == null || traceId == null || spanId == null || traceParent == null) {
            throw new IllegalArgumentException("correlation fields must not be null");
        }
    }
}
