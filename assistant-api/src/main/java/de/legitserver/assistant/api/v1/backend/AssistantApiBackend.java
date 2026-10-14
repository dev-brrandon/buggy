package de.legitserver.assistant.api.v1.backend;

import de.legitserver.assistant.api.v1.model.ApiCallContext;
import de.legitserver.assistant.api.v1.model.ApiCommandRequest;
import de.legitserver.assistant.api.v1.model.ApiMutationResult;
import de.legitserver.assistant.api.v1.model.ApiResourceView;
import de.legitserver.assistant.api.v1.pagination.ApiPage;
import de.legitserver.assistant.api.v1.pagination.ApiPageRequest;

/**
 * Application boundary behind HTTP. Phase-15 production wiring must delegate mutating execution to the
 * Phase-13 pipeline instead of bypassing security/policy/idempotency.
 */
public interface AssistantApiBackend {
    ApiPage<ApiResourceView> listAssistants(ApiCallContext context, ApiPageRequest page);
    ApiResourceView getAssistant(ApiCallContext context, String id);

    ApiMutationResult createConversation(ApiCallContext context, ApiCommandRequest request, String idempotencyKey);
    ApiResourceView getConversation(ApiCallContext context, String id);
    ApiMutationResult appendConversationMessage(ApiCallContext context, String id, ApiCommandRequest request, String idempotencyKey);
    ApiMutationResult cancelConversation(ApiCallContext context, String id, ApiCommandRequest request, String idempotencyKey);

    ApiMutationResult createBug(ApiCallContext context, ApiCommandRequest request, String idempotencyKey);
    ApiPage<ApiResourceView> listBugs(ApiCallContext context, ApiPageRequest page);
    ApiResourceView getBug(ApiCallContext context, String id);
    ApiMutationResult updateBugStatus(ApiCallContext context, String id, ApiCommandRequest request, String idempotencyKey);
    ApiMutationResult addBugRelation(ApiCallContext context, String id, ApiCommandRequest request, String idempotencyKey);

    ApiPage<ApiResourceView> searchKnowledge(ApiCallContext context, String query, ApiPageRequest page);
    ApiResourceView getKnowledgeDocument(ApiCallContext context, String id);

    ApiMutationResult createRequest(ApiCallContext context, ApiCommandRequest request, String idempotencyKey);
    ApiResourceView getRequest(ApiCallContext context, String id);
    ApiMutationResult approveRequest(ApiCallContext context, String id, ApiCommandRequest request, String idempotencyKey);
    ApiMutationResult denyRequest(ApiCallContext context, String id, ApiCommandRequest request, String idempotencyKey);
    ApiMutationResult cancelRequest(ApiCallContext context, String id, ApiCommandRequest request, String idempotencyKey);

    ApiPage<ApiResourceView> listSupervisorAssistants(ApiCallContext context, ApiPageRequest page);
    ApiResourceView getAssistantHealth(ApiCallContext context, String id);
    ApiPage<ApiResourceView> getAssistantFailures(ApiCallContext context, String id, ApiPageRequest page);
}
