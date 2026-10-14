package de.legitserver.assistant.api.v1.controller;

import de.legitserver.assistant.api.v1.AssistantApiV1;
import de.legitserver.assistant.api.v1.backend.AssistantApiBackend;
import de.legitserver.assistant.api.v1.model.ApiCommandRequest;
import de.legitserver.assistant.api.v1.model.ApiMutationResult;
import de.legitserver.assistant.api.v1.model.ApiResourceView;
import de.legitserver.assistant.api.v1.pagination.ApiPage;
import de.legitserver.assistant.api.v1.pagination.ApiPageRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(AssistantApiV1.BASE_PATH + "/bugs")
public final class BugController {
    private final AssistantApiBackend backend;
    public BugController(AssistantApiBackend backend) { this.backend = backend; }

    @PostMapping
    public ApiMutationResult create(HttpServletRequest request, @RequestBody ApiCommandRequest body,
            @RequestHeader(AssistantApiV1.IDEMPOTENCY_HEADER) String key) {
        return backend.createBug(ApiControllerSupport.context(request), body, ApiControllerSupport.idempotencyKey(key));
    }

    @GetMapping
    public ApiPage<ApiResourceView> list(HttpServletRequest request, @RequestParam(name = "cursor", required=false) String cursor,
            @RequestParam(name = "pageSize", required=false) Integer pageSize) {
        return backend.listBugs(ApiControllerSupport.context(request), ApiPageRequest.from(pageSize, cursor));
    }

    @GetMapping("/{id}")
    public ApiResourceView get(HttpServletRequest request, @PathVariable("id") String id) {
        return backend.getBug(ApiControllerSupport.context(request), ApiControllerSupport.resourceId(id));
    }

    @PostMapping("/{id}/status")
    public ApiMutationResult status(HttpServletRequest request, @PathVariable("id") String id, @RequestBody ApiCommandRequest body,
            @RequestHeader(AssistantApiV1.IDEMPOTENCY_HEADER) String key) {
        return backend.updateBugStatus(ApiControllerSupport.context(request), ApiControllerSupport.resourceId(id), body,
                ApiControllerSupport.idempotencyKey(key));
    }

    @PostMapping("/{id}/relations")
    public ApiMutationResult relation(HttpServletRequest request, @PathVariable("id") String id, @RequestBody ApiCommandRequest body,
            @RequestHeader(AssistantApiV1.IDEMPOTENCY_HEADER) String key) {
        return backend.addBugRelation(ApiControllerSupport.context(request), ApiControllerSupport.resourceId(id), body,
                ApiControllerSupport.idempotencyKey(key));
    }
}
