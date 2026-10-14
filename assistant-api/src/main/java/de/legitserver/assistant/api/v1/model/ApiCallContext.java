package de.legitserver.assistant.api.v1.model;

import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.panel.security.PanelGatewayVerifiedRequest;
import java.util.Objects;

/** Transport correlation plus verified Panel gateway identity. */
public record ApiCallContext(
        RequestId requestId,
        TraceId traceId,
        String authorization,
        PanelGatewayVerifiedRequest panelRequest) {
    public ApiCallContext {
        requestId = Objects.requireNonNull(requestId, "requestId");
        traceId = Objects.requireNonNull(traceId, "traceId");
        authorization = authorization == null ? "" : authorization.trim();
    }

    /** Legacy constructor retained for transport-contract self-tests that do not execute secured handlers. */
    public ApiCallContext(RequestId requestId, TraceId traceId, String authorization) {
        this(requestId, traceId, authorization, null);
    }

    public PanelGatewayVerifiedRequest requirePanelRequest() {
        if (panelRequest == null) throw new IllegalStateException("verified Panel request missing");
        return panelRequest;
    }
}
