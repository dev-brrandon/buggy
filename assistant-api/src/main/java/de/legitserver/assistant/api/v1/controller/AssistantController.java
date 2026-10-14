package de.legitserver.assistant.api.v1.controller;

import de.legitserver.assistant.api.v1.AssistantApiV1;
import de.legitserver.assistant.api.v1.backend.AssistantApiBackend;
import de.legitserver.assistant.api.v1.model.ApiResourceView;
import de.legitserver.assistant.api.v1.pagination.ApiPage;
import de.legitserver.assistant.api.v1.pagination.ApiPageRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AssistantApiV1.BASE_PATH + "/assistants")
public final class AssistantController {
    private final AssistantApiBackend backend;
    public AssistantController(AssistantApiBackend backend) { this.backend = backend; }

    @GetMapping
    public ApiPage<ApiResourceView> list(
            HttpServletRequest request,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "pageSize", required = false) Integer pageSize) {
        return backend.listAssistants(ApiControllerSupport.context(request), ApiPageRequest.from(pageSize, cursor));
    }

    @GetMapping("/{id}")
    public ApiResourceView get(HttpServletRequest request, @PathVariable("id") String id) {
        return backend.getAssistant(ApiControllerSupport.context(request), ApiControllerSupport.resourceId(id));
    }
}
