package de.legitserver.assistant.runtime.execution;

import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.observability.audit.AuditOutcome;
import de.legitserver.assistant.observability.audit.AuditUnavailableException;
import de.legitserver.assistant.observability.metrics.AssistantMetric;
import de.legitserver.assistant.observability.metrics.AssistantMetrics;
import de.legitserver.assistant.runtime.audit.ToolAudit;
import de.legitserver.assistant.runtime.model.ToolDescriptor;
import de.legitserver.assistant.runtime.model.ToolInvocation;
import de.legitserver.assistant.runtime.model.ToolInvocationResult;
import de.legitserver.assistant.runtime.model.ToolOutput;
import de.legitserver.assistant.runtime.policy.ToolAuthorizationPort;
import de.legitserver.assistant.runtime.policy.ToolRiskPolicy;
import de.legitserver.assistant.runtime.registry.RegisteredTool;
import de.legitserver.assistant.runtime.registry.ToolRegistry;
import de.legitserver.assistant.runtime.schema.ToolSchemaValidator;
import de.legitserver.assistant.runtime.schema.ToolSchemaViolationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Phase-10 controlled execution pipeline. */
public final class ToolRuntime {
    private final ToolRegistry registry;
    private final ToolAuthorizationPort authorization;
    private final ToolSchemaValidator schemas;
    private final ToolRiskPolicy riskPolicy;
    private final ToolOutputFilter outputFilter;
    private final ToolAudit audit;
    private final AssistantMetrics metrics;
    private final ExecutorService executor;
    private final Clock clock;

    public ToolRuntime(
            ToolRegistry registry,
            ToolAuthorizationPort authorization,
            ToolSchemaValidator schemas,
            ToolRiskPolicy riskPolicy,
            ToolOutputFilter outputFilter,
            ToolAudit audit,
            AssistantMetrics metrics,
            ExecutorService executor,
            Clock clock) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.schemas = Objects.requireNonNull(schemas, "schemas");
        this.riskPolicy = Objects.requireNonNull(riskPolicy, "riskPolicy");
        this.outputFilter = Objects.requireNonNull(outputFilter, "outputFilter");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ToolInvocationResult invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        RegisteredTool registered = registry.find(invocation.toolId())
                .orElseThrow(() -> new ToolRuntimeException(ToolFailureCode.TOOL_NOT_FOUND));
        ToolDescriptor descriptor = registered.descriptor();
        Instant started = clock.instant();
        try {
            var decision = authorization.authorize(descriptor, invocation);
            if (decision == null || !decision.allowed()) {
                auditSafe(descriptor, invocation, AuditOutcome.DENIED, "AUTHORIZATION_DENIED", started);
                throw new ToolRuntimeException(ToolFailureCode.AUTHORIZATION_DENIED);
            }
            validateInput(descriptor, invocation);
            DataClassification effectiveInput = DataClassification.max(
                    invocation.inputClassification(), invocation.resource().classification());
            if (!descriptor.classificationPolicy().allowsInput(effectiveInput)) {
                auditSafe(descriptor, invocation, AuditOutcome.DENIED, "INPUT_CLASSIFICATION_DENIED", started);
                throw new ToolRuntimeException(ToolFailureCode.INPUT_CLASSIFICATION_DENIED);
            }
            riskPolicy.authorize(descriptor, invocation, started);
            if (!registered.tryAcquire()) {
                auditSafe(descriptor, invocation, AuditOutcome.DENIED, "CONCURRENCY_LIMIT", started);
                throw new ToolRuntimeException(ToolFailureCode.CONCURRENCY_LIMIT);
            }
            try {
                auditPreExecution(descriptor, invocation, started);
                metrics.increment(AssistantMetric.TOOL_CALLS);
                ToolOutput raw = executeWithTimeout(registered, invocation, descriptor.timeout());
                try {
                    schemas.validate(descriptor.outputSchema(), raw.values());
                } catch (ToolSchemaViolationException invalid) {
                    throw new ToolRuntimeException(ToolFailureCode.OUTPUT_SCHEMA_INVALID, invalid);
                }
                if (!descriptor.classificationPolicy().allowsOutput(raw.classification())) {
                    throw new ToolRuntimeException(ToolFailureCode.OUTPUT_CLASSIFICATION_DENIED);
                }
                ToolOutput sanitized = outputFilter.sanitize(raw);
                Duration duration = Duration.between(started, clock.instant());
                auditSafe(descriptor, invocation, AuditOutcome.SUCCESS, "COMPLETED", clock.instant());
                return new ToolInvocationResult(descriptor.toolId(), sanitized.values(), sanitized.classification(), duration);
            } finally {
                registered.release();
            }
        } catch (ToolRuntimeException failure) {
            metrics.increment(AssistantMetric.TOOL_FAILURES);
            try {
                auditSafe(descriptor, invocation, AuditOutcome.FAILED, failure.code().name(), clock.instant());
            } catch (AuditUnavailableException unavailable) {
                throw new ToolRuntimeException(ToolFailureCode.AUDIT_UNAVAILABLE, unavailable);
            }
            throw failure;
        } catch (AuditUnavailableException unavailable) {
            metrics.increment(AssistantMetric.TOOL_FAILURES);
            throw new ToolRuntimeException(ToolFailureCode.AUDIT_UNAVAILABLE, unavailable);
        }
    }

    private void validateInput(ToolDescriptor descriptor, ToolInvocation invocation) {
        try {
            schemas.validate(descriptor.inputSchema(), invocation.input());
        } catch (ToolSchemaViolationException invalid) {
            throw new ToolRuntimeException(ToolFailureCode.INPUT_SCHEMA_INVALID, invalid);
        }
    }

    private ToolOutput executeWithTimeout(RegisteredTool registered, ToolInvocation invocation, Duration timeout) {
        Future<ToolOutput> future = executor.submit(() -> registered.handler().execute(
                new ToolExecutionContext(invocation.actor(), invocation.resource(), invocation.input())));
        try {
            ToolOutput output = future.get(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
            if (output == null) throw new ToolRuntimeException(ToolFailureCode.EXECUTION_FAILED);
            return output;
        } catch (TimeoutException timeoutException) {
            future.cancel(true);
            throw new ToolRuntimeException(ToolFailureCode.TIMEOUT);
        } catch (InterruptedException interrupted) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new ToolRuntimeException(ToolFailureCode.EXECUTION_FAILED, interrupted);
        } catch (ExecutionException execution) {
            Throwable cause = execution.getCause();
            if (cause instanceof ToolRuntimeException toolFailure) throw toolFailure;
            throw new ToolRuntimeException(ToolFailureCode.EXECUTION_FAILED);
        }
    }

    private void auditPreExecution(ToolDescriptor descriptor, ToolInvocation invocation, Instant at) {
        audit.record(descriptor, invocation, AuditOutcome.REQUESTED, "EXECUTION_REQUESTED", at);
    }

    private void auditSafe(ToolDescriptor descriptor, ToolInvocation invocation, AuditOutcome outcome, String stage, Instant at) {
        try {
            audit.record(descriptor, invocation, outcome, stage, at);
        } catch (AuditUnavailableException unavailable) {
            if (descriptor.auditPolicy().failClosed()) throw unavailable;
        }
    }
}
