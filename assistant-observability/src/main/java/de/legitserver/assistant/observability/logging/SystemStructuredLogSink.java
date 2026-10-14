package de.legitserver.assistant.observability.logging;

import java.util.Map;

/** JDK System.Logger sink producing one machine-readable JSON object per event. */
public final class SystemStructuredLogSink implements StructuredLogSink {
    private final System.Logger logger;

    public SystemStructuredLogSink(String loggerName) {
        if (loggerName == null || loggerName.isBlank()) throw new IllegalArgumentException("loggerName must not be blank");
        logger = System.getLogger(loggerName);
    }

    @Override
    public void write(StructuredLogEvent event) {
        logger.log(toSystemLevel(event.level()), toJson(event));
    }

    static String toJson(StructuredLogEvent event) {
        StringBuilder json = new StringBuilder(384);
        json.append('{');
        append(json, "timestamp", event.timestamp().toString());
        append(json, "level", event.level().name());
        append(json, "requestId", event.requestId().value());
        append(json, "traceId", event.traceId().value());
        append(json, "component", event.component());
        append(json, "operation", event.operation());
        append(json, "result", event.result());
        json.append(",\"durationMs\":").append(event.duration().toMillis());
        if (event.assistantId() != null) append(json, "assistantId", event.assistantId().name());
        if (event.actorId() != null) append(json, "actorId", event.actorId().value());
        for (Map.Entry<String, String> field : event.fields().entrySet()) {
            append(json, field.getKey(), field.getValue());
        }
        return json.append('}').toString();
    }

    private static void append(StringBuilder json, String key, String value) {
        if (json.length() > 1) json.append(',');
        json.append('"').append(escape(key)).append("\":\"").append(escape(value)).append('"');
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static System.Logger.Level toSystemLevel(LogLevel level) {
        return switch (level) {
            case TRACE -> System.Logger.Level.TRACE;
            case DEBUG -> System.Logger.Level.DEBUG;
            case INFO -> System.Logger.Level.INFO;
            case WARN -> System.Logger.Level.WARNING;
            case ERROR -> System.Logger.Level.ERROR;
        };
    }
}
