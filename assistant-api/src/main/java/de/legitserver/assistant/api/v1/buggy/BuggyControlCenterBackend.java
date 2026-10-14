package de.legitserver.assistant.api.v1.buggy;

import de.legitserver.assistant.api.v1.model.ApiCallContext;
import de.legitserver.assistant.api.v1.model.ApiResourceView;
import de.legitserver.assistant.api.v1.pagination.ApiPage;
import de.legitserver.assistant.api.v1.pagination.ApiPageRequest;

/** Stable B8 application boundary behind the HTTP transport. All methods are read-only. */
public interface BuggyControlCenterBackend {
    ApiResourceView overview(ApiCallContext context);
    ApiResourceView identity(ApiCallContext context);
    ApiResourceView personality(ApiCallContext context);
    ApiPage<ApiResourceView> tasks(ApiCallContext context, ApiPageRequest page);
    ApiPage<ApiResourceView> activity(ApiCallContext context, ApiPageRequest page);
    ApiPage<ApiResourceView> approvals(ApiCallContext context, ApiPageRequest page);
    ApiPage<ApiResourceView> incidents(ApiCallContext context, ApiPageRequest page);
    ApiPage<ApiResourceView> knowledge(ApiCallContext context, String query, ApiPageRequest page);
    ApiPage<ApiResourceView> selfDevelopment(ApiCallContext context, ApiPageRequest page);
    ApiPage<ApiResourceView> automations(ApiCallContext context, ApiPageRequest page);
    ApiPage<ApiResourceView> tools(ApiCallContext context, ApiPageRequest page);
    ApiResourceView security(ApiCallContext context);
    ApiPage<ApiResourceView> audit(ApiCallContext context, String query, ApiPageRequest page);
}
