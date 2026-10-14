package de.legitserver.assistant.api.v1.buggy;

import de.legitserver.assistant.api.v1.model.ApiCallContext;
import de.legitserver.assistant.api.v1.model.ApiResourceView;
import de.legitserver.assistant.api.v1.pagination.ApiPage;
import de.legitserver.assistant.api.v1.pagination.ApiPageRequest;

/**
 * Read-through boundary for Foundation-owned projections that B8 must display without copying their state.
 * Implementations must delegate to authoritative Foundation read models; no Panel database is permitted.
 */
public interface BuggyControlCenterExternalReadPort {
    ApiResourceView summary(ApiCallContext context);
    ApiPage<ApiResourceView> approvals(ApiCallContext context, ApiPageRequest page);
    ApiPage<ApiResourceView> incidents(ApiCallContext context, ApiPageRequest page);
    ApiPage<ApiResourceView> knowledge(ApiCallContext context, String query, ApiPageRequest page);
    ApiPage<ApiResourceView> audit(ApiCallContext context, String query, ApiPageRequest page);
}
