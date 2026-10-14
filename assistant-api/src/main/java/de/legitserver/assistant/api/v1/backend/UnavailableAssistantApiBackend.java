package de.legitserver.assistant.api.v1.backend;

import de.legitserver.assistant.api.v1.error.ApiException;
import de.legitserver.assistant.api.v1.model.ApiCallContext;
import de.legitserver.assistant.api.v1.model.ApiCommandRequest;
import de.legitserver.assistant.api.v1.model.ApiMutationResult;
import de.legitserver.assistant.api.v1.model.ApiResourceView;
import de.legitserver.assistant.api.v1.pagination.ApiPage;
import de.legitserver.assistant.api.v1.pagination.ApiPageRequest;
import org.springframework.http.HttpStatus;

/** Fail-closed placeholder until Phase-15 production backend wiring is installed. */
public final class UnavailableAssistantApiBackend implements AssistantApiBackend {
    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "API.BACKEND_UNAVAILABLE",
                "Assistant API backend is not configured", true);
    }

    @Override public ApiPage<ApiResourceView> listAssistants(ApiCallContext c, ApiPageRequest p) { throw unavailable(); }
    @Override public ApiResourceView getAssistant(ApiCallContext c, String id) { throw unavailable(); }
    @Override public ApiMutationResult createConversation(ApiCallContext c, ApiCommandRequest r, String k) { throw unavailable(); }
    @Override public ApiResourceView getConversation(ApiCallContext c, String id) { throw unavailable(); }
    @Override public ApiMutationResult appendConversationMessage(ApiCallContext c, String id, ApiCommandRequest r, String k) { throw unavailable(); }
    @Override public ApiMutationResult cancelConversation(ApiCallContext c, String id, ApiCommandRequest r, String k) { throw unavailable(); }
    @Override public ApiMutationResult createBug(ApiCallContext c, ApiCommandRequest r, String k) { throw unavailable(); }
    @Override public ApiPage<ApiResourceView> listBugs(ApiCallContext c, ApiPageRequest p) { throw unavailable(); }
    @Override public ApiResourceView getBug(ApiCallContext c, String id) { throw unavailable(); }
    @Override public ApiMutationResult updateBugStatus(ApiCallContext c, String id, ApiCommandRequest r, String k) { throw unavailable(); }
    @Override public ApiMutationResult addBugRelation(ApiCallContext c, String id, ApiCommandRequest r, String k) { throw unavailable(); }
    @Override public ApiPage<ApiResourceView> searchKnowledge(ApiCallContext c, String q, ApiPageRequest p) { throw unavailable(); }
    @Override public ApiResourceView getKnowledgeDocument(ApiCallContext c, String id) { throw unavailable(); }
    @Override public ApiMutationResult createRequest(ApiCallContext c, ApiCommandRequest r, String k) { throw unavailable(); }
    @Override public ApiResourceView getRequest(ApiCallContext c, String id) { throw unavailable(); }
    @Override public ApiMutationResult approveRequest(ApiCallContext c, String id, ApiCommandRequest r, String k) { throw unavailable(); }
    @Override public ApiMutationResult denyRequest(ApiCallContext c, String id, ApiCommandRequest r, String k) { throw unavailable(); }
    @Override public ApiMutationResult cancelRequest(ApiCallContext c, String id, ApiCommandRequest r, String k) { throw unavailable(); }
    @Override public ApiPage<ApiResourceView> listSupervisorAssistants(ApiCallContext c, ApiPageRequest p) { throw unavailable(); }
    @Override public ApiResourceView getAssistantHealth(ApiCallContext c, String id) { throw unavailable(); }
    @Override public ApiPage<ApiResourceView> getAssistantFailures(ApiCallContext c, String id, ApiPageRequest p) { throw unavailable(); }
}
