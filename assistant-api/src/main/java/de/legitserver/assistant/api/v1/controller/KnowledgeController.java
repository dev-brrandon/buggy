package de.legitserver.assistant.api.v1.controller;

import de.legitserver.assistant.api.v1.AssistantApiV1;
import de.legitserver.assistant.api.v1.backend.AssistantApiBackend;
import de.legitserver.assistant.api.v1.model.ApiResourceView;
import de.legitserver.assistant.api.v1.pagination.ApiPage;
import de.legitserver.assistant.api.v1.pagination.ApiPageRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(AssistantApiV1.BASE_PATH + "/knowledge")
public final class KnowledgeController {
    private final AssistantApiBackend backend;
    public KnowledgeController(AssistantApiBackend backend) { this.backend = backend; }

    @GetMapping("/search")
    public ApiPage<ApiResourceView> search(HttpServletRequest request, @RequestParam(name = "query") String query,
            @RequestParam(name = "cursor", required=false) String cursor, @RequestParam(name = "pageSize", required=false) Integer pageSize) {
        if (query == null || query.isBlank() || query.length() > 512) throw new IllegalArgumentException("query invalid");
        return backend.searchKnowledge(ApiControllerSupport.context(request), query.strip(), ApiPageRequest.from(pageSize, cursor));
    }

    @GetMapping("/documents/{id}")
    public ApiResourceView document(HttpServletRequest request, @PathVariable("id") String id) {
        return backend.getKnowledgeDocument(ApiControllerSupport.context(request), ApiControllerSupport.resourceId(id));
    }
}
