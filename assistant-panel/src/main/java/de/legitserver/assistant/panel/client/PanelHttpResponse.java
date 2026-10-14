package de.legitserver.assistant.panel.client;

import java.util.List;
import java.util.Map;

/** Sanitized HTTP response returned to the Panel Backend integration layer. */
public record PanelHttpResponse(int statusCode, String body, Map<String, List<String>> headers) {
    public PanelHttpResponse {
        if (statusCode < 100 || statusCode > 599) throw new IllegalArgumentException("statusCode invalid");
        body = body == null ? "" : body;
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public boolean successful() { return statusCode >= 200 && statusCode < 300; }
    public boolean degraded() { return statusCode == 503; }
}
