package de.legitserver.assistant.pipeline.model;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.core.request.RequestContext;
import de.legitserver.assistant.core.validation.CoreValidation;
import de.legitserver.assistant.requests.model.ApprovalRequestId;
import de.legitserver.assistant.security.authorization.ResourceRef;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** Immutable request accepted by the framework-free Phase-13 pipeline. */
public record PipelineRequest(
        RequestContext context,
        String serviceCredential,
        ActorContext presentedActor,
        AssistantDescriptor assistant,
        PipelineOperation operation,
        ResourceRef resource,
        Map<String, Object> input,
        DataClassification inputClassification,
        DataClassification responseClassificationCeiling,
        ApprovalRequestId approvalRequestId,
        String idempotencyKey,
        Duration timeout) {
    public PipelineRequest {
        context = Objects.requireNonNull(context, "context");
        serviceCredential = CoreValidation.requireText(serviceCredential, "serviceCredential", 8192);
        presentedActor = Objects.requireNonNull(presentedActor, "presentedActor");
        assistant = Objects.requireNonNull(assistant, "assistant");
        operation = Objects.requireNonNull(operation, "operation");
        resource = Objects.requireNonNull(resource, "resource");
        input = input == null ? Map.of() : Map.copyOf(input);
        inputClassification = Objects.requireNonNull(inputClassification, "inputClassification");
        responseClassificationCeiling = Objects.requireNonNull(responseClassificationCeiling, "responseClassificationCeiling");
        idempotencyKey = CoreValidation.optionalSafeToken(idempotencyKey, "idempotencyKey", 128);
        timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalArgumentException("timeout must be > 0 and <= 2 minutes");
        }
        if (!context.requestId().equals(presentedActor.requestId()) || !context.traceId().equals(presentedActor.traceId())) {
            throw new IllegalArgumentException("request/trace correlation mismatch");
        }
        if (!context.actorId().equals(presentedActor.actorId())) {
            throw new IllegalArgumentException("context actor mismatch");
        }
        if (context.assistantId() != null && context.assistantId() != assistant.assistantId()) {
            throw new IllegalArgumentException("context assistant mismatch");
        }
    }
}
