package de.legitserver.assistant.api.v1.controller;

import de.legitserver.assistant.api.v1.AssistantApiV1;
import de.legitserver.assistant.api.v1.backend.AssistantApiBackend;
import de.legitserver.assistant.api.v1.model.ApiResourceView;
import de.legitserver.assistant.api.v1.pagination.ApiPage;
import de.legitserver.assistant.api.v1.pagination.ApiPageRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(AssistantApiV1.BASE_PATH + "/supervisor/assistants")
public final class SupervisorController {
    private final AssistantApiBackend backend;
    public SupervisorController(AssistantApiBackend backend) { this.backend = backend; }

    @GetMapping
    public ApiPage<ApiResourceView> list(HttpServletRequest request, @RequestParam(name = "cursor", required=false) String cursor,
            @RequestParam(name = "pageSize", required=false) Integer pageSize) {
        return backend.listSupervisorAssistants(ApiControllerSupport.context(request), ApiPageRequest.from(pageSize, cursor));
    }

    @GetMapping("/{id}/health")
    public ApiResourceView health(HttpServletRequest request, @PathVariable("id") String id) {
        return backend.getAssistantHealth(ApiControllerSupport.context(request), ApiControllerSupport.resourceId(id));
    }

    @GetMapping("/{id}/failures")
    public ApiPage<ApiResourceView> failures(HttpServletRequest request, @PathVariable("id") String id,
            @RequestParam(name = "cursor", required=false) String cursor, @RequestParam(name = "pageSize", required=false) Integer pageSize) {
        return backend.getAssistantFailures(ApiControllerSupport.context(request), ApiControllerSupport.resourceId(id),
                ApiPageRequest.from(pageSize, cursor));
    }
}
