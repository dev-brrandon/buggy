package de.legitserver.assistant.observability.authorization;

import de.legitserver.assistant.core.request.RequestContext;
import de.legitserver.assistant.observability.audit.AuditAction;
import de.legitserver.assistant.observability.audit.AuditOutcome;
import de.legitserver.assistant.observability.audit.AuditRecord;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.observability.logging.LogLevel;
import de.legitserver.assistant.observability.logging.SanitizedStructuredLogger;
import de.legitserver.assistant.observability.logging.StructuredLogEvent;
import de.legitserver.assistant.observability.metrics.AssistantMetric;
import de.legitserver.assistant.observability.metrics.AssistantMetrics;
import de.legitserver.assistant.security.authorization.AssistantAuthorizationService;
import de.legitserver.assistant.security.authorization.AuthorizationDecision;
import de.legitserver.assistant.security.authorization.AuthorizationRequest;
import de.legitserver.assistant.security.authorization.ResourceRef;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Adds safe metrics, structured logging, and denial audit to the canonical authorization pipeline. */
public final class ObservedAuthorizationService {
    private final AssistantAuthorizationService delegate;
    private final AuditRecorder audit;
    private final AssistantMetrics metrics;
    private final SanitizedStructuredLogger logger;

    public ObservedAuthorizationService(
            AssistantAuthorizationService delegate,
            AuditRecorder audit,
            AssistantMetrics metrics,
            SanitizedStructuredLogger logger) {
        if (delegate == null || audit == null || metrics == null || logger == null) {
            throw new IllegalArgumentException("authorization observability dependency must not be null");
        }
        this.delegate = delegate;
        this.audit = audit;
        this.metrics = metrics;
        this.logger = logger;
    }

    public AuthorizationDecision authorize(RequestContext context, AuthorizationRequest request) {
        if (context == null || request == null) throw new IllegalArgumentException("context and request must not be null");
        long started = System.nanoTime();
        AuthorizationDecision decision = delegate.authorize(request);
        Duration duration = Duration.ofNanos(Math.max(0L, System.nanoTime() - started));
        if (!decision.allowed()) {
            metrics.increment(AssistantMetric.AUTHORIZATION_DENIALS);
            auditDenial(context, request, decision);
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("capability", request.capabilityId().value());
        fields.put("decision", decision.allowed() ? "ALLOW" : "DENY");
        fields.put("reason", decision.denialReason().name());
        fields.put("securityRevision", Long.toString(decision.securityRevision()));
        logger.log(new StructuredLogEvent(
                Instant.now(),
                decision.allowed() ? LogLevel.DEBUG : LogLevel.WARN,
                context.requestId(),
                context.traceId(),
                "authorization",
                "authorize:" + request.capabilityId().value(),
                decision.allowed() ? "ALLOW" : "DENIED",
                duration,
                request.assistant().assistantId(),
                request.actor().actorId(),
                fields));
        return decision;
    }

    private void auditDenial(RequestContext context, AuthorizationRequest request, AuthorizationDecision decision) {
        ResourceRef resource = request.resource();
        String target = resource == null ? request.capabilityId().value() : resource.resourceType() + ":" + resource.resourceId();
        Map<String, String> details = new LinkedHashMap<>();
        details.put("capability", request.capabilityId().value());
        details.put("denialReason", decision.denialReason().name());
        details.put("securityRevision", Long.toString(decision.securityRevision()));
        details.put("riskLevel", decision.riskLevel() == null ? "UNKNOWN" : decision.riskLevel().name());
        audit.append(new AuditRecord(
                AuditAction.SENSITIVE_AUTHORIZATION_DENIAL,
                AuditOutcome.DENIED,
                context.requestId(),
                context.traceId(),
                request.actor().actorId(),
                request.actor().minecraftUuid(),
                request.assistant().assistantId(),
                target,
                context.serverId(),
                resource == null ? "capability" : resource.resourceType(),
                resource == null ? request.capabilityId().value() : resource.resourceId(),
                Instant.now(),
                details,
                false));
    }
}
