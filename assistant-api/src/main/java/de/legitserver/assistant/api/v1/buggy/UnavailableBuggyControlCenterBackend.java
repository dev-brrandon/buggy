package de.legitserver.assistant.api.v1.buggy;

import de.legitserver.assistant.api.v1.error.ApiException;
import de.legitserver.assistant.api.v1.model.ApiCallContext;
import de.legitserver.assistant.api.v1.model.ApiResourceView;
import de.legitserver.assistant.api.v1.pagination.ApiPage;
import de.legitserver.assistant.api.v1.pagination.ApiPageRequest;
import org.springframework.http.HttpStatus;

/** Fail-closed B8 backend used until all canonical read/security dependencies are wired. */
public final class UnavailableBuggyControlCenterBackend implements BuggyControlCenterBackend {
    private static ApiException unavailable() { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "API.BUGGY_CONTROL_CENTER_UNAVAILABLE", "Buggy Control Center backend unavailable", true); }
    @Override public ApiResourceView overview(ApiCallContext c) { throw unavailable(); }
    @Override public ApiResourceView identity(ApiCallContext c) { throw unavailable(); }
    @Override public ApiResourceView personality(ApiCallContext c) { throw unavailable(); }
    @Override public ApiPage<ApiResourceView> tasks(ApiCallContext c, ApiPageRequest p) { throw unavailable(); }
    @Override public ApiPage<ApiResourceView> activity(ApiCallContext c, ApiPageRequest p) { throw unavailable(); }
    @Override public ApiPage<ApiResourceView> approvals(ApiCallContext c, ApiPageRequest p) { throw unavailable(); }
    @Override public ApiPage<ApiResourceView> incidents(ApiCallContext c, ApiPageRequest p) { throw unavailable(); }
    @Override public ApiPage<ApiResourceView> knowledge(ApiCallContext c, String q, ApiPageRequest p) { throw unavailable(); }
    @Override public ApiPage<ApiResourceView> selfDevelopment(ApiCallContext c, ApiPageRequest p) { throw unavailable(); }
    @Override public ApiPage<ApiResourceView> automations(ApiCallContext c, ApiPageRequest p) { throw unavailable(); }
    @Override public ApiPage<ApiResourceView> tools(ApiCallContext c, ApiPageRequest p) { throw unavailable(); }
    @Override public ApiResourceView security(ApiCallContext c) { throw unavailable(); }
    @Override public ApiPage<ApiResourceView> audit(ApiCallContext c, String q, ApiPageRequest p) { throw unavailable(); }
}
