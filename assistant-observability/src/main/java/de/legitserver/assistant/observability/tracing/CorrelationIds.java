package de.legitserver.assistant.observability.tracing;

import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Generates and validates safe request IDs plus OpenTelemetry/W3C-compatible trace identifiers. */
public final class CorrelationIds {
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern TRACE_ID = Pattern.compile("[0-9a-fA-F]{32}");
    private static final Pattern TRACEPARENT = Pattern.compile("([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private CorrelationIds() {}

    public static TraceCorrelation resolve(String presentedRequestId, String presentedTraceId, String traceParent) {
        RequestId requestId = new RequestId(validRequestId(presentedRequestId) ? presentedRequestId : generatedRequestId());
        String traceId = validTraceId(presentedTraceId) ? presentedTraceId.toLowerCase(Locale.ROOT) : traceIdFromTraceParent(traceParent);
        if (traceId == null) traceId = randomHex(16);
        String spanId = randomNonZeroHex(8);
        String canonicalTraceParent = "00-" + traceId + "-" + spanId + "-01";
        return new TraceCorrelation(requestId, new TraceId(traceId), spanId, canonicalTraceParent);
    }

    public static boolean validRequestId(String value) {
        return value != null && REQUEST_ID.matcher(value).matches();
    }

    public static boolean validTraceId(String value) {
        return value != null && TRACE_ID.matcher(value).matches() && !allZero(value);
    }

    static String traceIdFromTraceParent(String value) {
        if (value == null) return null;
        Matcher matcher = TRACEPARENT.matcher(value);
        if (!matcher.matches() || "ff".equals(matcher.group(1)) || allZero(matcher.group(2)) || allZero(matcher.group(3))) {
            return null;
        }
        return matcher.group(2);
    }

    private static String generatedRequestId() {
        return "req-" + randomHex(16);
    }

    private static String randomNonZeroHex(int bytes) {
        String value;
        do {
            value = randomHex(bytes);
        } while (allZero(value));
        return value;
    }

    private static String randomHex(int bytes) {
        byte[] data = new byte[bytes];
        RANDOM.nextBytes(data);
        return HEX.formatHex(data);
    }

    private static boolean allZero(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) != '0') return false;
        }
        return true;
    }
}
