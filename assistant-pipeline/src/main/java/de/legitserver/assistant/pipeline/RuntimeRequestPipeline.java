package de.legitserver.assistant.pipeline;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.observability.metrics.AssistantMetric;
import de.legitserver.assistant.observability.metrics.AssistantMetrics;
import de.legitserver.assistant.pipeline.audit.PipelineAudit;
import de.legitserver.assistant.pipeline.control.AdmissionDecision;
import de.legitserver.assistant.pipeline.control.BoundedAdmissionController;
import de.legitserver.assistant.pipeline.control.IdempotencyCoordinator;
import de.legitserver.assistant.pipeline.control.IdempotencyDecision;
import de.legitserver.assistant.pipeline.control.IdempotencyStatus;
import de.legitserver.assistant.pipeline.control.PipelineCancellationRegistry;
import de.legitserver.assistant.pipeline.control.PipelineCanonicalizer;
import de.legitserver.assistant.pipeline.model.PipelineExecutionContext;
import de.legitserver.assistant.pipeline.model.PipelineExecutionOutput;
import de.legitserver.assistant.pipeline.model.PipelineRequest;
import de.legitserver.assistant.pipeline.model.PipelineResponse;
import de.legitserver.assistant.pipeline.model.PipelineRoute;
import de.legitserver.assistant.pipeline.model.PipelineStage;
import de.legitserver.assistant.pipeline.model.PipelineStatus;
import de.legitserver.assistant.pipeline.policy.PipelinePolicyDecision;
import de.legitserver.assistant.pipeline.policy.RuntimePipelinePolicy;
import de.legitserver.assistant.pipeline.port.PipelineActorResolution;
import de.legitserver.assistant.pipeline.port.PipelineActorResolverPort;
import de.legitserver.assistant.pipeline.port.PipelineAuthenticationPort;
import de.legitserver.assistant.pipeline.port.PipelineAuthorizationDecision;
import de.legitserver.assistant.pipeline.port.PipelineAuthorizationPort;
import de.legitserver.assistant.pipeline.port.PipelineCapabilityResolverPort;
import de.legitserver.assistant.pipeline.port.PipelineConversationResolution;
import de.legitserver.assistant.pipeline.port.PipelineConversationResolverPort;
import de.legitserver.assistant.pipeline.port.PipelineExecutionPort;
import de.legitserver.assistant.pipeline.port.PipelineFormatterPort;
import de.legitserver.assistant.pipeline.port.PipelineResponseSecurityPort;
import de.legitserver.assistant.pipeline.port.PipelineRouterPort;
import de.legitserver.assistant.pipeline.port.PipelineStageObserver;
import de.legitserver.assistant.pipeline.response.PipelineResponseSecurityException;
import de.legitserver.assistant.runtime.execution.ToolFailureCode;
import de.legitserver.assistant.runtime.execution.ToolRuntimeException;
import de.legitserver.assistant.security.authn.ServiceAuthenticationResult;
import de.legitserver.assistant.security.authn.ServicePrincipal;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Phase-13 canonical runtime orchestrator. Every route passes authentication, actor resolution,
 * ingress authorization, conversation/capability/route resolution, policy, execution, response
 * security, formatting and request audit in that order.
 */
public final class RuntimeRequestPipeline {
    private final PipelineAuthenticationPort authentication;
    private final PipelineActorResolverPort actors;
    private final PipelineAuthorizationPort ingressAuthorization;
    private final PipelineConversationResolverPort conversations;
    private final PipelineCapabilityResolverPort capabilities;
    private final PipelineRouterPort router;
    private final RuntimePipelinePolicy policy;
    private final PipelineExecutionPort execution;
    private final PipelineResponseSecurityPort responseSecurity;
    private final PipelineFormatterPort formatter;
    private final PipelineAudit audit;
    private final BoundedAdmissionController admission;
    private final IdempotencyCoordinator idempotency;
    private final PipelineCancellationRegistry cancellations;
    private final ExecutorService executor;
    private final AssistantMetrics metrics;
    private final PipelineStageObserver stages;
    private final Clock clock;
    private final Duration maxQueueWait;

    public RuntimeRequestPipeline(
            PipelineAuthenticationPort authentication,
            PipelineActorResolverPort actors,
            PipelineAuthorizationPort ingressAuthorization,
            PipelineConversationResolverPort conversations,
            PipelineCapabilityResolverPort capabilities,
            PipelineRouterPort router,
            RuntimePipelinePolicy policy,
            PipelineExecutionPort execution,
            PipelineResponseSecurityPort responseSecurity,
            PipelineFormatterPort formatter,
            PipelineAudit audit,
            BoundedAdmissionController admission,
            IdempotencyCoordinator idempotency,
            PipelineCancellationRegistry cancellations,
            ExecutorService executor,
            AssistantMetrics metrics,
            PipelineStageObserver stages,
            Clock clock,
            Duration maxQueueWait) {
        if (authentication == null || actors == null || ingressAuthorization == null || conversations == null
                || capabilities == null || router == null || policy == null || execution == null
                || responseSecurity == null || formatter == null || audit == null || admission == null
                || idempotency == null || cancellations == null || executor == null || metrics == null
                || stages == null || clock == null || maxQueueWait == null) {
            throw new IllegalArgumentException("pipeline dependencies must not be null");
        }
        if (maxQueueWait.isZero() || maxQueueWait.isNegative()) {
            throw new IllegalArgumentException("maxQueueWait must be positive");
        }
        this.authentication = authentication;
        this.actors = actors;
        this.ingressAuthorization = ingressAuthorization;
        this.conversations = conversations;
        this.capabilities = capabilities;
        this.router = router;
        this.policy = policy;
        this.execution = execution;
        this.responseSecurity = responseSecurity;
        this.formatter = formatter;
        this.audit = audit;
        this.admission = admission;
        this.idempotency = idempotency;
        this.cancellations = cancellations;
        this.executor = executor;
        this.metrics = metrics;
        this.stages = stages;
        this.clock = clock;
        this.maxQueueWait = maxQueueWait;
    }

    public PipelineResponse execute(PipelineRequest request) {
        if (request == null) throw new IllegalArgumentException("request must not be null");
        long startedNanos = System.nanoTime();
        metrics.increment(AssistantMetric.REQUEST_COUNT);
        try {
            cancellations.register(request.context().requestId());
        } catch (IllegalStateException duplicate) {
            return immediate(request, PipelineStatus.BACKPRESSURE, "REQUEST_ALREADY_ACTIVE", startedNanos);
        }
        IdempotencyClaim claim = IdempotencyClaim.none();
        try {
            stage(request, PipelineStage.INCOMING_REQUEST);
            if (cancelled(request)) return terminal(request, PipelineStatus.CANCELLED, "CANCELLED", startedNanos);

            stage(request, PipelineStage.AUTHENTICATION);
            ServiceAuthenticationResult authn = authentication.authenticate(request, clock.instant());
            if (authn == null || !authn.authenticated()) {
                String code = authn == null ? "AUTHENTICATION_UNAVAILABLE" : authn.status().name();
                return terminal(request, PipelineStatus.AUTHENTICATION_FAILED, code, startedNanos);
            }
            ServicePrincipal principal = authn.principal();

            stage(request, PipelineStage.ACTOR_RESOLUTION);
            PipelineActorResolution actorResolution = actors.resolve(request, principal);
            if (actorResolution == null || !actorResolution.resolved()) {
                return terminal(request, PipelineStatus.ACTOR_RESOLUTION_FAILED,
                        actorResolution == null ? "ACTOR_RESOLUTION_UNAVAILABLE" : actorResolution.safeCode(), startedNanos);
            }
            ActorContext actor = actorResolution.actor();
            if (cancelled(request)) return terminal(request, PipelineStatus.CANCELLED, "CANCELLED", startedNanos);

            stage(request, PipelineStage.AUTHORIZATION);
            PipelineAuthorizationDecision ingress = ingressAuthorization.authorize(request, principal, actor);
            if (ingress == null || !ingress.allowed()) {
                return terminal(request, PipelineStatus.AUTHORIZATION_DENIED,
                        ingress == null ? "AUTHORIZATION_UNAVAILABLE" : ingress.safeCode(), startedNanos);
            }

            stage(request, PipelineStage.CONVERSATION_RESOLUTION);
            PipelineConversationResolution conversation = conversations.resolve(request, actor);
            if (conversation == null || !conversation.accepted()) {
                return terminal(request, PipelineStatus.CONVERSATION_UNAVAILABLE,
                        conversation == null ? "CONVERSATION_RESOLUTION_UNAVAILABLE" : conversation.safeCode(), startedNanos);
            }

            stage(request, PipelineStage.CAPABILITY_RESOLUTION);
            Optional<AssistantCapabilityId> resolvedCapability = capabilities.resolve(request.operation());
            if (resolvedCapability.isEmpty()) {
                return terminal(request, PipelineStatus.CAPABILITY_UNAVAILABLE, "CAPABILITY_UNRESOLVED", startedNanos);
            }
            AssistantCapabilityId capability = resolvedCapability.orElseThrow();

            stage(request, PipelineStage.TOOL_DOMAIN_ROUTING);
            Optional<PipelineRoute> resolvedRoute = router.route(request.operation(), capability);
            if (resolvedRoute.isEmpty()) {
                return terminal(request, PipelineStatus.ROUTE_UNAVAILABLE, "ROUTE_UNRESOLVED", startedNanos);
            }
            PipelineRoute route = resolvedRoute.orElseThrow();
            PipelineExecutionContext executionContext = new PipelineExecutionContext(
                    request, principal, actor, conversation.conversation(), capability, route);

            stage(request, PipelineStage.POLICY);
            PipelinePolicyDecision policyDecision = policy.evaluate(executionContext);
            if (policyDecision == null || !policyDecision.allowed()) {
                String code = policyDecision == null ? "POLICY_UNAVAILABLE" : policyDecision.safeCode();
                return terminal(request, policyStatus(code), code, startedNanos);
            }
            if (cancelled(request)) return terminal(request, PipelineStatus.CANCELLED, "CANCELLED", startedNanos);

            String requestHash;
            try {
                requestHash = PipelineCanonicalizer.requestHash(request);
            } catch (IllegalArgumentException invalidInput) {
                return terminal(request, PipelineStatus.POLICY_DENIED, "REQUEST_CANONICALIZATION_DENIED", startedNanos);
            }
            IdempotencyDecision idempotencyDecision = idempotency.claim(request, actor.actorId(), requestHash);
            if (idempotencyDecision.status() == IdempotencyStatus.CONFLICT) {
                return terminal(request, PipelineStatus.IDEMPOTENCY_CONFLICT, "IDEMPOTENCY_PAYLOAD_CONFLICT", startedNanos);
            }
            if (idempotencyDecision.status() == IdempotencyStatus.IN_FLIGHT) {
                return terminal(request, PipelineStatus.BACKPRESSURE, "IDEMPOTENCY_IN_FLIGHT", startedNanos);
            }
            if (idempotencyDecision.status() == IdempotencyStatus.REPLAY) {
                PipelineResponse replay = idempotencyDecision.replay().asReplay(
                        request.context().requestId(), request.context().traceId(), elapsed(startedNanos));
                stage(request, PipelineStage.AUDIT);
                safeAudit(request, replay);
                stage(request, PipelineStage.RESPONSE);
                metrics.recordLatency(AssistantMetric.REQUEST_LATENCY, replay.duration());
                return replay;
            }
            claim = new IdempotencyClaim(actor.actorId(), requestHash, true);

            Duration queueWait = minDuration(maxQueueWait, request.timeout());
            AdmissionDecision admissionDecision = admission.acquire(request.context().requestId(), cancellations, queueWait);
            if (admissionDecision != AdmissionDecision.ACQUIRED) {
                idempotency.abandon(request, actor.actorId(), requestHash);
                claim = IdempotencyClaim.none();
                PipelineStatus status = admissionDecision == AdmissionDecision.CANCELLED
                        ? PipelineStatus.CANCELLED : PipelineStatus.BACKPRESSURE;
                return terminal(request, status, admissionDecision.name(), startedNanos);
            }
            PipelineExecutionOutput raw;
            try {
                stage(request, PipelineStage.EXECUTION);
                Future<PipelineExecutionOutput> future = executor.submit(() -> execution.execute(executionContext));
                cancellations.attachFuture(request.context().requestId(), future);
                Duration timeout = minDuration(request.timeout(), route.executionTimeout());
                try {
                    raw = future.get(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
                    if (raw == null) throw new IllegalStateException("null execution output");
                } catch (CancellationException cancelled) {
                    idempotency.abandon(request, actor.actorId(), requestHash);
                    claim = IdempotencyClaim.none();
                    return terminal(request, PipelineStatus.CANCELLED, "CANCELLED", startedNanos);
                } catch (TimeoutException timeoutFailure) {
                    future.cancel(true);
                    idempotency.abandon(request, actor.actorId(), requestHash);
                    claim = IdempotencyClaim.none();
                    return terminal(request, PipelineStatus.TIMEOUT, "TIMEOUT", startedNanos);
                } catch (InterruptedException interrupted) {
                    future.cancel(true);
                    Thread.currentThread().interrupt();
                    idempotency.abandon(request, actor.actorId(), requestHash);
                    claim = IdempotencyClaim.none();
                    return terminal(request, PipelineStatus.CANCELLED, "INTERRUPTED", startedNanos);
                } catch (ExecutionException executionFailure) {
                    idempotency.abandon(request, actor.actorId(), requestHash);
                    claim = IdempotencyClaim.none();
                    return executionFailure(request, executionFailure.getCause(), startedNanos);
                }
            } finally {
                admission.release();
            }

            stage(request, PipelineStage.RESPONSE_SECURITY);
            PipelineExecutionOutput secured;
            try {
                secured = responseSecurity.secure(executionContext, raw);
            } catch (PipelineResponseSecurityException denied) {
                idempotency.abandon(request, actor.actorId(), requestHash);
                claim = IdempotencyClaim.none();
                return terminal(request, PipelineStatus.RESPONSE_REJECTED, denied.getMessage(), startedNanos);
            } catch (RuntimeException denied) {
                idempotency.abandon(request, actor.actorId(), requestHash);
                claim = IdempotencyClaim.none();
                return terminal(request, PipelineStatus.RESPONSE_REJECTED, "RESPONSE_SECURITY_FAILED", startedNanos);
            }

            stage(request, PipelineStage.FORMATTING);
            String body;
            try {
                body = formatter.format(executionContext, secured);
            } catch (RuntimeException formattingFailure) {
                idempotency.abandon(request, actor.actorId(), requestHash);
                claim = IdempotencyClaim.none();
                return terminal(request, PipelineStatus.RESPONSE_REJECTED, "FORMATTING_FAILED", startedNanos);
            }
            PipelineResponse success = new PipelineResponse(
                    request.context().requestId(),
                    request.context().traceId(),
                    PipelineStatus.SUCCESS,
                    "OK",
                    secured.values(),
                    secured.classification(),
                    body,
                    elapsed(startedNanos),
                    false);
            stage(request, PipelineStage.AUDIT);
            safeAudit(request, success);
            idempotency.complete(request, actor.actorId(), requestHash, success);
            claim = IdempotencyClaim.none();
            stage(request, PipelineStage.RESPONSE);
            metrics.recordLatency(AssistantMetric.REQUEST_LATENCY, success.duration());
            return success;
        } catch (RuntimeException unexpected) {
            if (claim.owned) idempotency.abandon(request, claim.actorId, claim.requestHash);
            return terminal(request, PipelineStatus.EXECUTION_FAILED, "PIPELINE_INTERNAL_FAILURE", startedNanos);
        } finally {
            cancellations.complete(request.context().requestId());
        }
    }

    public boolean cancel(de.legitserver.assistant.core.request.RequestId requestId) {
        return cancellations.cancel(requestId);
    }

    private PipelineResponse executionFailure(PipelineRequest request, Throwable cause, long startedNanos) {
        if (cause instanceof ToolRuntimeException toolFailure) {
            ToolFailureCode code = toolFailure.code();
            if (code == ToolFailureCode.TIMEOUT) return terminal(request, PipelineStatus.TIMEOUT, "TOOL_TIMEOUT", startedNanos);
            if (code == ToolFailureCode.AUDIT_UNAVAILABLE) {
                return terminal(request, PipelineStatus.AUDIT_UNAVAILABLE, "TOOL_AUDIT_UNAVAILABLE", startedNanos);
            }
            if (code == ToolFailureCode.AUTHORIZATION_DENIED) {
                return terminal(request, PipelineStatus.AUTHORIZATION_DENIED, "TOOL_AUTHORIZATION_DENIED", startedNanos);
            }
            if (code == ToolFailureCode.CONCURRENCY_LIMIT) {
                return terminal(request, PipelineStatus.BACKPRESSURE, "TOOL_CONCURRENCY_LIMIT", startedNanos);
            }
            return terminal(request, PipelineStatus.EXECUTION_FAILED, "TOOL_" + code.name(), startedNanos);
        }
        return terminal(request, PipelineStatus.EXECUTION_FAILED, "EXECUTION_FAILED", startedNanos);
    }

    private PipelineResponse terminal(PipelineRequest request, PipelineStatus status, String safeCode, long startedNanos) {
        PipelineResponse response = immediate(request, status, safeCode, startedNanos);
        stage(request, PipelineStage.AUDIT);
        safeAudit(request, response);
        stage(request, PipelineStage.RESPONSE);
        metrics.increment(AssistantMetric.REQUEST_ERRORS);
        metrics.recordLatency(AssistantMetric.REQUEST_LATENCY, response.duration());
        return response;
    }

    private PipelineResponse immediate(PipelineRequest request, PipelineStatus status, String safeCode, long startedNanos) {
        return new PipelineResponse(
                request.context().requestId(),
                request.context().traceId(),
                status,
                safeCode,
                java.util.Map.of(),
                DataClassification.INTERNAL,
                safeCode,
                elapsed(startedNanos),
                false);
    }

    private void safeAudit(PipelineRequest request, PipelineResponse response) {
        try {
            audit.record(request, response);
        } catch (RuntimeException ignored) {
            // Request-level observability remains non-blocking. Security-critical operations audit fail-closed upstream.
        }
    }

    private boolean cancelled(PipelineRequest request) {
        return cancellations.cancelled(request.context().requestId());
    }

    private void stage(PipelineRequest request, PipelineStage stage) {
        stages.entered(request.context().requestId(), stage);
    }

    private static PipelineStatus policyStatus(String safeCode) {
        if ("RATE_LIMITED".equals(safeCode)) return PipelineStatus.RATE_LIMITED;
        if (safeCode != null && safeCode.startsWith("DEPENDENCY_")) return PipelineStatus.DEPENDENCY_DEGRADED;
        if ("CAPABILITY_AUTHORIZATION_DENIED".equals(safeCode)) return PipelineStatus.AUTHORIZATION_DENIED;
        return PipelineStatus.POLICY_DENIED;
    }

    private static Duration minDuration(Duration left, Duration right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static Duration elapsed(long startedNanos) {
        return Duration.ofNanos(Math.max(0L, System.nanoTime() - startedNanos));
    }

    private record IdempotencyClaim(de.legitserver.assistant.core.actor.ActorId actorId, String requestHash, boolean owned) {
        private static IdempotencyClaim none() {
            return new IdempotencyClaim(null, null, false);
        }
    }
}
