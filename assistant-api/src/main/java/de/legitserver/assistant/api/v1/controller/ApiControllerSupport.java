package de.legitserver.assistant.api.v1.controller;

import de.legitserver.assistant.api.observability.RequestCorrelationFilter;
import de.legitserver.assistant.api.v1.AssistantApiV1;
import de.legitserver.assistant.api.v1.model.ApiCallContext;
import de.legitserver.assistant.api.v1.security.PanelGatewayRequestAttributes;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.observability.tracing.TraceCorrelation;
import de.legitserver.assistant.panel.security.PanelGatewayVerifiedRequest;
import jakarta.servlet.http.HttpServletRequest;

final class ApiControllerSupport {
    private ApiControllerSupport() {}

    static ApiCallContext context(HttpServletRequest request) {
        Object raw = request.getAttribute(RequestCorrelationFilter.CORRELATION_ATTRIBUTE);
        if (!(raw instanceof TraceCorrelation correlation)) {
            throw new IllegalStateException("request correlation missing");
        }
        Object verifiedRaw = request.getAttribute(PanelGatewayRequestAttributes.VERIFIED);
        if (!(verifiedRaw instanceof PanelGatewayVerifiedRequest verified)) {
            throw new IllegalStateException("verified Panel gateway request missing");
        }
        return new ApiCallContext(
                new RequestId(correlation.requestId().value()),
                new TraceId(correlation.traceId().value()),
                request.getHeader("Authorization"),
                verified);
    }

    static String idempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128 || !key.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException(AssistantApiV1.IDEMPOTENCY_HEADER + " must be a safe token of 1..128 chars");
        }
        return key;
    }

    static String resourceId(String value) {
        if (value == null || value.isBlank() || value.length() > 192 || !value.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("resource id invalid");
        }
        return value;
    }
}
