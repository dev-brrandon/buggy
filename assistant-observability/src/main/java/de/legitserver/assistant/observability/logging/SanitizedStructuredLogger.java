package de.legitserver.assistant.observability.logging;

/** Guarantees a final sanitization pass immediately before log emission. */
public final class SanitizedStructuredLogger {
    private final TelemetrySanitizer sanitizer;
    private final StructuredLogSink sink;

    public SanitizedStructuredLogger(TelemetrySanitizer sanitizer, StructuredLogSink sink) {
        if (sanitizer == null || sink == null) throw new IllegalArgumentException("logger dependency must not be null");
        this.sanitizer = sanitizer;
        this.sink = sink;
    }

    public boolean log(StructuredLogEvent event) {
        if (event == null) return false;
        StructuredLogEvent sanitized = new StructuredLogEvent(
                event.timestamp(),
                event.level(),
                event.requestId(),
                event.traceId(),
                event.component(),
                sanitizer.sanitizeOperation(event.operation()),
                event.result(),
                event.duration(),
                event.assistantId(),
                event.actorId(),
                sanitizer.sanitizeMap(event.fields()));
        try {
            sink.write(sanitized);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
