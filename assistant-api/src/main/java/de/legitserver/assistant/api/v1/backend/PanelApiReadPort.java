package de.legitserver.assistant.api.v1.backend;

import de.legitserver.assistant.api.v1.model.ApiCallContext;
import de.legitserver.assistant.api.v1.model.ApiResourceView;
import de.legitserver.assistant.api.v1.pagination.ApiPage;
import de.legitserver.assistant.api.v1.pagination.ApiPageRequest;

/** Secured read-model boundary used by the Phase-15 API backend; implementations must not expose direct Panel DB access. */
public interface PanelApiReadPort {
    ApiPage<ApiResourceView> listAssistants(ApiCallContext context, ApiPageRequest page);
    ApiResourceView getAssistant(ApiCallContext context, String id);
    ApiResourceView getConversation(ApiCallContext context, String id);
    ApiPage<ApiResourceView> listBugs(ApiCallContext context, ApiPageRequest page);
    ApiResourceView getBug(ApiCallContext context, String id);
    ApiPage<ApiResourceView> searchKnowledge(ApiCallContext context, String query, ApiPageRequest page);
    ApiResourceView getKnowledgeDocument(ApiCallContext context, String id);
    ApiResourceView getRequest(ApiCallContext context, String id);
    ApiPage<ApiResourceView> listSupervisorAssistants(ApiCallContext context, ApiPageRequest page);
    ApiResourceView getAssistantHealth(ApiCallContext context, String id);
    ApiPage<ApiResourceView> getAssistantFailures(ApiCallContext context, String id, ApiPageRequest page);
}
