package de.legitserver.assistant.api.v1.controller;

import de.legitserver.assistant.api.v1.AssistantApiV1;
import de.legitserver.assistant.api.v1.backend.AssistantApiBackend;
import de.legitserver.assistant.api.v1.model.ApiCommandRequest;
import de.legitserver.assistant.api.v1.model.ApiMutationResult;
import de.legitserver.assistant.api.v1.model.ApiResourceView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(AssistantApiV1.BASE_PATH + "/conversations")
public final class ConversationController {
    private final AssistantApiBackend backend;
    public ConversationController(AssistantApiBackend backend) { this.backend = backend; }

    @PostMapping
    public ApiMutationResult create(HttpServletRequest request, @RequestBody ApiCommandRequest body,
            @RequestHeader(AssistantApiV1.IDEMPOTENCY_HEADER) String key) {
        return backend.createConversation(ApiControllerSupport.context(request), body, ApiControllerSupport.idempotencyKey(key));
    }

    @GetMapping("/{id}")
    public ApiResourceView get(HttpServletRequest request, @PathVariable("id") String id) {
        return backend.getConversation(ApiControllerSupport.context(request), ApiControllerSupport.resourceId(id));
    }

    @PostMapping("/{id}/messages")
    public ApiMutationResult message(HttpServletRequest request, @PathVariable("id") String id, @RequestBody ApiCommandRequest body,
            @RequestHeader(AssistantApiV1.IDEMPOTENCY_HEADER) String key) {
        return backend.appendConversationMessage(ApiControllerSupport.context(request), ApiControllerSupport.resourceId(id), body,
                ApiControllerSupport.idempotencyKey(key));
    }

    @PostMapping("/{id}/cancel")
    public ApiMutationResult cancel(HttpServletRequest request, @PathVariable("id") String id, @RequestBody ApiCommandRequest body,
            @RequestHeader(AssistantApiV1.IDEMPOTENCY_HEADER) String key) {
        return backend.cancelConversation(ApiControllerSupport.context(request), ApiControllerSupport.resourceId(id), body,
                ApiControllerSupport.idempotencyKey(key));
    }
}
