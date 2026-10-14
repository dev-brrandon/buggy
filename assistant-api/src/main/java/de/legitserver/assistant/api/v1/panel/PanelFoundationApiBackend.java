package de.legitserver.assistant.api.v1.panel;

import de.legitserver.assistant.api.v1.model.ApiCallContext;
import de.legitserver.assistant.api.v1.model.ApiResourceView;
import de.legitserver.assistant.api.v1.pagination.ApiPage;
import de.legitserver.assistant.api.v1.pagination.ApiPageRequest;
import de.legitserver.assistant.panel.state.PanelIntegrationHealth;

/** Read-only Panel foundation surfaces that are not part of a single Assistant domain aggregate. */
public interface PanelFoundationApiBackend {
    PanelIntegrationHealth status(ApiCallContext context);
    ApiPage<ApiResourceView> searchAudit(ApiCallContext context, String query, ApiPageRequest page);
    ApiResourceView rateLimits(ApiCallContext context);
    ApiResourceView featureFlags(ApiCallContext context);
}
