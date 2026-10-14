package de.legitserver.assistant.api.v1.controller;

import de.legitserver.assistant.api.v1.AssistantApiV1;
import de.legitserver.assistant.api.v1.backend.AssistantApiBackend;
import de.legitserver.assistant.api.v1.model.ApiCommandRequest;
import de.legitserver.assistant.api.v1.model.ApiMutationResult;
import de.legitserver.assistant.api.v1.model.ApiResourceView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(AssistantApiV1.BASE_PATH + "/requests")
public final class RequestController {
    private final AssistantApiBackend backend;
    public RequestController(AssistantApiBackend backend) { this.backend = backend; }

    @PostMapping
    public ApiMutationResult create(HttpServletRequest request, @RequestBody ApiCommandRequest body,
            @RequestHeader(AssistantApiV1.IDEMPOTENCY_HEADER) String key) {
        return backend.createRequest(ApiControllerSupport.context(request), body, ApiControllerSupport.idempotencyKey(key));
    }

    @GetMapping("/{id}")
    public ApiResourceView get(HttpServletRequest request, @PathVariable("id") String id) {
        return backend.getRequest(ApiControllerSupport.context(request), ApiControllerSupport.resourceId(id));
    }

    @PostMapping("/{id}/approve")
    public ApiMutationResult approve(HttpServletRequest request, @PathVariable("id") String id, @RequestBody ApiCommandRequest body,
            @RequestHeader(AssistantApiV1.IDEMPOTENCY_HEADER) String key) {
        return backend.approveRequest(ApiControllerSupport.context(request), ApiControllerSupport.resourceId(id), body,
                ApiControllerSupport.idempotencyKey(key));
    }

    @PostMapping("/{id}/deny")
    public ApiMutationResult deny(HttpServletRequest request, @PathVariable("id") String id, @RequestBody ApiCommandRequest body,
            @RequestHeader(AssistantApiV1.IDEMPOTENCY_HEADER) String key) {
        return backend.denyRequest(ApiControllerSupport.context(request), ApiControllerSupport.resourceId(id), body,
                ApiControllerSupport.idempotencyKey(key));
    }

    @PostMapping("/{id}/cancel")
    public ApiMutationResult cancel(HttpServletRequest request, @PathVariable("id") String id, @RequestBody ApiCommandRequest body,
            @RequestHeader(AssistantApiV1.IDEMPOTENCY_HEADER) String key) {
        return backend.cancelRequest(ApiControllerSupport.context(request), ApiControllerSupport.resourceId(id), body,
                ApiControllerSupport.idempotencyKey(key));
    }
}
