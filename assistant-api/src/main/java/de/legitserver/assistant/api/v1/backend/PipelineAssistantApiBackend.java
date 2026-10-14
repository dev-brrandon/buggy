package de.legitserver.assistant.api.v1.backend;

import de.legitserver.assistant.api.v1.error.ApiException;
import de.legitserver.assistant.api.v1.model.ApiCallContext;
import de.legitserver.assistant.api.v1.model.ApiCommandRequest;
import de.legitserver.assistant.api.v1.model.ApiMutationResult;
import de.legitserver.assistant.api.v1.model.ApiResourceView;
import de.legitserver.assistant.api.v1.pagination.ApiPage;
import de.legitserver.assistant.api.v1.pagination.ApiPageRequest;
import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.assistant.AssistantDescriptor;
import de.legitserver.assistant.core.request.RequestContext;
import de.legitserver.assistant.panel.security.PanelPipelineCredentialIssuer;
import de.legitserver.assistant.pipeline.RuntimeRequestPipeline;
import de.legitserver.assistant.pipeline.model.PipelineOperation;
import de.legitserver.assistant.pipeline.model.PipelineRequest;
import de.legitserver.assistant.pipeline.model.PipelineResponse;
import de.legitserver.assistant.pipeline.model.PipelineStatus;
import de.legitserver.assistant.security.authorization.ResourceRef;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Phase-15 production backend shape: every unsafe API mutation enters the canonical Phase-13 pipeline.
 * Reads stay behind a separate secured read-model port and never query a Panel database.
 */
public final class PipelineAssistantApiBackend implements AssistantApiBackend {
    private static final PipelineOperation CONVERSATION_CREATE = new PipelineOperation("assistant.conversation.create");
    private static final PipelineOperation CONVERSATION_MESSAGE = new PipelineOperation("assistant.conversation.message.append");
    private static final PipelineOperation CONVERSATION_CANCEL = new PipelineOperation("assistant.conversation.cancel");
    private static final PipelineOperation BUG_CREATE = new PipelineOperation("assistant.bug.create");
    private static final PipelineOperation BUG_STATUS = new PipelineOperation("assistant.bug.status.update");
    private static final PipelineOperation BUG_RELATION = new PipelineOperation("assistant.bug.relation.add");
    private static final PipelineOperation REQUEST_CREATE = new PipelineOperation("assistant.request.create");
    private static final PipelineOperation REQUEST_APPROVE = new PipelineOperation("assistant.request.approve");
    private static final PipelineOperation REQUEST_DENY = new PipelineOperation("assistant.request.deny");
    private static final PipelineOperation REQUEST_CANCEL = new PipelineOperation("assistant.request.cancel");

    private final RuntimeRequestPipeline pipeline;
    private final PanelPipelineCredentialIssuer credentials;
    private final PanelAssistantResolutionPort assistants;
    private final PanelPipelineOperationPolicyPort policies;
    private final PanelApiReadPort reads;
    private final Clock clock;

    public PipelineAssistantApiBackend(
            RuntimeRequestPipeline pipeline,
            PanelPipelineCredentialIssuer credentials,
            PanelAssistantResolutionPort assistants,
            PanelPipelineOperationPolicyPort policies,
            PanelApiReadPort reads,
            Clock clock) {
        if (pipeline == null || credentials == null || assistants == null || policies == null || reads == null || clock == null) {
            throw new IllegalArgumentException("Pipeline Assistant API backend dependencies required");
        }
        this.pipeline = pipeline;
        this.credentials = credentials;
        this.assistants = assistants;
        this.policies = policies;
        this.reads = reads;
        this.clock = clock;
    }

    @Override public ApiPage<ApiResourceView> listAssistants(ApiCallContext c, ApiPageRequest p) { return reads.listAssistants(c, p); }
    @Override public ApiResourceView getAssistant(ApiCallContext c, String id) { return reads.getAssistant(c, id); }
    @Override public ApiResourceView getConversation(ApiCallContext c, String id) { return reads.getConversation(c, id); }
    @Override public ApiPage<ApiResourceView> listBugs(ApiCallContext c, ApiPageRequest p) { return reads.listBugs(c, p); }
    @Override public ApiResourceView getBug(ApiCallContext c, String id) { return reads.getBug(c, id); }
    @Override public ApiPage<ApiResourceView> searchKnowledge(ApiCallContext c, String q, ApiPageRequest p) { return reads.searchKnowledge(c, q, p); }
    @Override public ApiResourceView getKnowledgeDocument(ApiCallContext c, String id) { return reads.getKnowledgeDocument(c, id); }
    @Override public ApiResourceView getRequest(ApiCallContext c, String id) { return reads.getRequest(c, id); }
    @Override public ApiPage<ApiResourceView> listSupervisorAssistants(ApiCallContext c, ApiPageRequest p) { return reads.listSupervisorAssistants(c, p); }
    @Override public ApiResourceView getAssistantHealth(ApiCallContext c, String id) { return reads.getAssistantHealth(c, id); }
    @Override public ApiPage<ApiResourceView> getAssistantFailures(ApiCallContext c, String id, ApiPageRequest p) { return reads.getAssistantFailures(c, id, p); }

    @Override
    public ApiMutationResult createConversation(ApiCallContext context, ApiCommandRequest request, String key) {
        return mutate(context, CONVERSATION_CREATE, null, "conversation", request, key);
    }

    @Override
    public ApiMutationResult appendConversationMessage(ApiCallContext context, String id, ApiCommandRequest request, String key) {
        return mutate(context, CONVERSATION_MESSAGE, id, "conversation", request, key);
    }

    @Override
    public ApiMutationResult cancelConversation(ApiCallContext context, String id, ApiCommandRequest request, String key) {
        return mutate(context, CONVERSATION_CANCEL, id, "conversation", request, key);
    }

    @Override
    public ApiMutationResult createBug(ApiCallContext context, ApiCommandRequest request, String key) {
        return mutate(context, BUG_CREATE, null, "bug", request, key);
    }

    @Override
    public ApiMutationResult updateBugStatus(ApiCallContext context, String id, ApiCommandRequest request, String key) {
        return mutate(context, BUG_STATUS, id, "bug", request, key);
    }

    @Override
    public ApiMutationResult addBugRelation(ApiCallContext context, String id, ApiCommandRequest request, String key) {
        return mutate(context, BUG_RELATION, id, "bug", request, key);
    }

    @Override
    public ApiMutationResult createRequest(ApiCallContext context, ApiCommandRequest request, String key) {
        return mutate(context, REQUEST_CREATE, null, "approval_request", request, key);
    }

    @Override
    public ApiMutationResult approveRequest(ApiCallContext context, String id, ApiCommandRequest request, String key) {
        return mutate(context, REQUEST_APPROVE, id, "approval_request", request, key);
    }

    @Override
    public ApiMutationResult denyRequest(ApiCallContext context, String id, ApiCommandRequest request, String key) {
        return mutate(context, REQUEST_DENY, id, "approval_request", request, key);
    }

    @Override
    public ApiMutationResult cancelRequest(ApiCallContext context, String id, ApiCommandRequest request, String key) {
        return mutate(context, REQUEST_CANCEL, id, "approval_request", request, key);
    }

    private ApiMutationResult mutate(
            ApiCallContext context,
            PipelineOperation operation,
            String resourceId,
            String resourceType,
            ApiCommandRequest command,
            String idempotencyKey) {
        ActorContext actor = context.requirePanelRequest().presentedActor();
        AssistantDescriptor assistant = assistants.resolve(context, operation, resourceId, command);
        if (assistant == null || !assistant.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "API.ASSISTANT_UNAVAILABLE", "Assistant is unavailable", true);
        }
        PanelPipelineOperationPolicy policy = policies.policy(operation);
        if (policy == null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "API.POLICY_UNAVAILABLE", "API policy unavailable", true);
        String safeResourceId = resourceId == null ? "new-" + context.requestId().value() : resourceId;
        Map<String, Object> input = new LinkedHashMap<>(command.data());
        if (resourceId != null) input.put("resourceId", resourceId);
        RequestContext requestContext = new RequestContext(
                context.requestId(), context.traceId(), actor.actorId(), actor.origin(), assistant.assistantId(),
                resourceType.equals("conversation") ? resourceId : null, actor.serverId(), clock.instant());
        PipelineRequest pipelineRequest = new PipelineRequest(
                requestContext,
                credentials.issue(context.requestId()),
                actor,
                assistant,
                operation,
                new ResourceRef(resourceType, safeResourceId, policy.inputClassification()),
                input,
                policy.inputClassification(),
                policy.responseClassificationCeiling(),
                null,
                idempotencyKey,
                policy.timeout());
        PipelineResponse response = pipeline.execute(pipelineRequest);
        if (!response.successful()) throw mapFailure(response);
        String resultId = response.output().get("id") instanceof String id && !id.isBlank() ? id : safeResourceId;
        Map<String, Object> attributes = new LinkedHashMap<>(response.output());
        attributes.putIfAbsent("pipelineStatus", response.status().name());
        attributes.putIfAbsent("safeCode", response.safeCode());
        return new ApiMutationResult(new ApiResourceView(resultId, attributes), response.idempotentReplay());
    }

    private static ApiException mapFailure(PipelineResponse response) {
        HttpStatus status = switch (response.status()) {
            case AUTHENTICATION_FAILED -> HttpStatus.UNAUTHORIZED;
            case ACTOR_RESOLUTION_FAILED, AUTHORIZATION_DENIED, POLICY_DENIED -> HttpStatus.FORBIDDEN;
            case IDEMPOTENCY_CONFLICT -> HttpStatus.CONFLICT;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case BACKPRESSURE, DEPENDENCY_DEGRADED, CONVERSATION_UNAVAILABLE, CAPABILITY_UNAVAILABLE,
                    ROUTE_UNAVAILABLE, AUDIT_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case CANCELLED -> HttpStatus.CONFLICT;
            case RESPONSE_REJECTED -> HttpStatus.valueOf(422);
            case EXECUTION_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
            case SUCCESS -> throw new IllegalArgumentException("cannot map successful pipeline response as failure");
        };
        boolean retryable = switch (response.status()) {
            case BACKPRESSURE, DEPENDENCY_DEGRADED, RATE_LIMITED, TIMEOUT, AUDIT_UNAVAILABLE,
                    CONVERSATION_UNAVAILABLE, CAPABILITY_UNAVAILABLE, ROUTE_UNAVAILABLE -> true;
            default -> false;
        };
        return new ApiException(status, "API.PIPELINE_" + response.status().name(), "Assistant request was not completed", retryable,
                Map.of("safeCode", response.safeCode()));
    }
}
