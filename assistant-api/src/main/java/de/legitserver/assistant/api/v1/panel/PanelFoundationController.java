package de.legitserver.assistant.api.v1.panel;

import de.legitserver.assistant.api.v1.AssistantApiV1;
import de.legitserver.assistant.api.v1.controller.ApiControllerSupportBridge;
import de.legitserver.assistant.api.v1.model.ApiResourceView;
import de.legitserver.assistant.api.v1.pagination.ApiPage;
import de.legitserver.assistant.api.v1.pagination.ApiPageRequest;
import de.legitserver.assistant.panel.state.PanelIntegrationHealth;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AssistantApiV1.BASE_PATH + "/panel")
public final class PanelFoundationController {
    private final PanelFoundationApiBackend backend;

    public PanelFoundationController(PanelFoundationApiBackend backend) {
        this.backend = backend;
    }

    @GetMapping("/status")
    public PanelIntegrationHealth status(HttpServletRequest request) {
        return backend.status(ApiControllerSupportBridge.context(request));
    }

    @GetMapping("/audit")
    public ApiPage<ApiResourceView> audit(
            HttpServletRequest request,
            @RequestParam(name = "q", required = false, defaultValue = "") String query,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        if (query.length() > 256) throw new IllegalArgumentException("audit query too long");
        return backend.searchAudit(ApiControllerSupportBridge.context(request), query, ApiPageRequest.from(pageSize, cursor));
    }

    @GetMapping("/rate-limits")
    public ApiResourceView rateLimits(HttpServletRequest request) {
        return backend.rateLimits(ApiControllerSupportBridge.context(request));
    }

    @GetMapping("/feature-flags")
    public ApiResourceView featureFlags(HttpServletRequest request) {
        return backend.featureFlags(ApiControllerSupportBridge.context(request));
    }
}
