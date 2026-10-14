package de.legitserver.assistant.observability.logging;

/** Output boundary for already-sanitized structured log events. */
@FunctionalInterface
public interface StructuredLogSink {
    void write(StructuredLogEvent event);
}
