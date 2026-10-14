package de.legitserver.assistant.pipeline.policy;

import de.legitserver.assistant.observability.health.DependencyHealth;
import de.legitserver.assistant.observability.health.DependencyHealthRegistry;
import de.legitserver.assistant.observability.health.DependencyState;
import de.legitserver.assistant.observability.metrics.AssistantMetric;
import de.legitserver.assistant.observability.metrics.AssistantMetrics;
import de.legitserver.assistant.pipeline.model.PipelineExecutionContext;
import de.legitserver.assistant.security.authorization.AuthorizationDecision;

/** Canonical Phase-13 policy gate after capability and route resolution. */
public final class RuntimePipelinePolicy {
    private final PipelineCapabilityAuthorizationPort authorization;
    private final DependencyHealthRegistry dependencies;
    private final SlidingWindowRateLimiter rateLimiter;
    private final AssistantMetrics metrics;

    public RuntimePipelinePolicy(
            PipelineCapabilityAuthorizationPort authorization,
            DependencyHealthRegistry dependencies,
            SlidingWindowRateLimiter rateLimiter,
            AssistantMetrics metrics) {
        if (authorization == null || dependencies == null || rateLimiter == null || metrics == null) {
            throw new IllegalArgumentException("policy dependencies must not be null");
        }
        this.authorization = authorization;
        this.dependencies = dependencies;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
    }

    public PipelinePolicyDecision evaluate(PipelineExecutionContext context) {
        AuthorizationDecision decision = authorization.authorize(context);
        if (decision == null || !decision.allowed()) return PipelinePolicyDecision.deny("CAPABILITY_AUTHORIZATION_DENIED");
        for (var dependencyId : context.route().requiredDependencies()) {
            DependencyHealth health = dependencies.get(dependencyId);
            if (health == null || health.state() != DependencyState.UP) {
                return PipelinePolicyDecision.deny("DEPENDENCY_" + dependencyId.name() + "_UNAVAILABLE");
            }
        }
        RateLimitKey key = new RateLimitKey(
                context.actor().actorId(), context.request().assistant().assistantId(), context.capabilityId());
        if (!rateLimiter.tryAcquire(key)) {
            metrics.increment(AssistantMetric.RATE_LIMIT_HITS);
            return PipelinePolicyDecision.deny("RATE_LIMITED");
        }
        return PipelinePolicyDecision.allow();
    }
}
