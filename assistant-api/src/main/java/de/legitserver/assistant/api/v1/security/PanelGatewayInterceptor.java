package de.legitserver.assistant.api.v1.security;

import de.legitserver.assistant.api.observability.RequestCorrelationFilter;
import de.legitserver.assistant.api.v1.AssistantApiV1;
import de.legitserver.assistant.api.v1.error.ApiException;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.observability.tracing.TraceCorrelation;
import de.legitserver.assistant.panel.security.PanelGatewayAuthenticationPort;
import de.legitserver.assistant.panel.security.PanelGatewayAuthenticationResult;
import de.legitserver.assistant.panel.security.PanelGatewayAuthenticationStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

/** Authenticates the Panel Backend and its signed request-bound user context before controller execution. */
public final class PanelGatewayInterceptor implements HandlerInterceptor {
    private final PanelGatewayAuthenticationPort authentication;
    private final Clock clock;

    public PanelGatewayInterceptor(PanelGatewayAuthenticationPort authentication, Clock clock) {
        if (authentication == null || clock == null) {
            throw new IllegalArgumentException("Panel gateway interceptor dependencies required");
        }
        this.authentication = authentication;
        this.clock = clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object raw = request.getAttribute(RequestCorrelationFilter.CORRELATION_ATTRIBUTE);
        if (!(raw instanceof TraceCorrelation correlation)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "API.CORRELATION_MISSING", "Request correlation missing", true);
        }
        PanelGatewayAuthenticationResult result = authentication.authenticate(
                request.getHeader("Authorization"),
                request.getHeader(AssistantApiV1.PANEL_CONTEXT_HEADER),
                new RequestId(correlation.requestId().value()),
                new TraceId(correlation.traceId().value()),
                clock.instant());
        if (!result.authenticated()) {
            if (result.status() == PanelGatewayAuthenticationStatus.UNAVAILABLE) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "API.PANEL_SECURITY_UNAVAILABLE",
                        "Panel gateway security is not configured", true);
            }
            HttpStatus status = result.status() == PanelGatewayAuthenticationStatus.SERVICE_AUTHENTICATION_FAILED
                    ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN;
            throw new ApiException(status, "API.PANEL_AUTHENTICATION_DENIED", "Panel request authentication failed", false);
        }
        request.setAttribute(PanelGatewayRequestAttributes.VERIFIED, result.verified());
        return true;
    }
}
