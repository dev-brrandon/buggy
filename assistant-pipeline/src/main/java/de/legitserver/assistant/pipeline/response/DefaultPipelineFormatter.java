package de.legitserver.assistant.pipeline.response;

import de.legitserver.assistant.observability.logging.TelemetrySanitizer;
import de.legitserver.assistant.pipeline.model.PipelineExecutionContext;
import de.legitserver.assistant.pipeline.model.PipelineExecutionOutput;
import de.legitserver.assistant.pipeline.port.PipelineFormatterPort;
import java.util.Comparator;
import java.util.Map;

/** Deterministic bounded text formatter; the structured output remains authoritative for Phase 14. */
public final class DefaultPipelineFormatter implements PipelineFormatterPort {
    private static final int MAX_BODY = 16_384;
    private final TelemetrySanitizer sanitizer = TelemetrySanitizer.standard();

    @Override
    public String format(PipelineExecutionContext context, PipelineExecutionOutput output) {
        if (context == null || output == null) throw new IllegalArgumentException("format arguments must not be null");
        StringBuilder body = new StringBuilder();
        output.values().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(entry -> body.append(entry.getKey())
                        .append('=')
                        .append(sanitizer.sanitizeValue(String.valueOf(entry.getValue())))
                        .append('\n'));
        if (body.length() > MAX_BODY) return body.substring(0, MAX_BODY - 12) + "[TRUNCATED]";
        return body.toString();
    }
}
