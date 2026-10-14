package de.legitserver.assistant.api.v1.buggy;

import de.legitserver.assistant.api.v1.AssistantApiV1;
import de.legitserver.assistant.api.v1.controller.ApiControllerSupportBridge;
import de.legitserver.assistant.api.v1.model.ApiResourceView;
import de.legitserver.assistant.api.v1.pagination.ApiPage;
import de.legitserver.assistant.api.v1.pagination.ApiPageRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** B8 read-only Buggy Control Center API. Mutations remain on existing Phase-13 pipeline-backed endpoints. */
@RestController
@RequestMapping(AssistantApiV1.BASE_PATH + "/buggy")
public final class BuggyControlCenterController {
    private final BuggyControlCenterBackend backend;
    public BuggyControlCenterController(BuggyControlCenterBackend backend) { this.backend = backend; }

    @GetMapping("/overview") public ApiResourceView overview(HttpServletRequest r) { return backend.overview(ApiControllerSupportBridge.context(r)); }
    @GetMapping("/identity") public ApiResourceView identity(HttpServletRequest r) { return backend.identity(ApiControllerSupportBridge.context(r)); }
    @GetMapping("/personality") public ApiResourceView personality(HttpServletRequest r) { return backend.personality(ApiControllerSupportBridge.context(r)); }
    @GetMapping("/tasks") public ApiPage<ApiResourceView> tasks(HttpServletRequest r, @RequestParam(name="cursor",required=false) String c, @RequestParam(name="pageSize",required=false) Integer s) { return backend.tasks(ApiControllerSupportBridge.context(r), ApiPageRequest.from(s,c)); }
    @GetMapping("/activity") public ApiPage<ApiResourceView> activity(HttpServletRequest r, @RequestParam(name="cursor",required=false) String c, @RequestParam(name="pageSize",required=false) Integer s) { return backend.activity(ApiControllerSupportBridge.context(r), ApiPageRequest.from(s,c)); }
    @GetMapping("/approvals") public ApiPage<ApiResourceView> approvals(HttpServletRequest r, @RequestParam(name="cursor",required=false) String c, @RequestParam(name="pageSize",required=false) Integer s) { return backend.approvals(ApiControllerSupportBridge.context(r), ApiPageRequest.from(s,c)); }
    @GetMapping("/incidents") public ApiPage<ApiResourceView> incidents(HttpServletRequest r, @RequestParam(name="cursor",required=false) String c, @RequestParam(name="pageSize",required=false) Integer s) { return backend.incidents(ApiControllerSupportBridge.context(r), ApiPageRequest.from(s,c)); }
    @GetMapping("/knowledge") public ApiPage<ApiResourceView> knowledge(HttpServletRequest r, @RequestParam(name="q",required=false,defaultValue="") String q, @RequestParam(name="cursor",required=false) String c, @RequestParam(name="pageSize",required=false) Integer s) { requireQuery(q); return backend.knowledge(ApiControllerSupportBridge.context(r), q, ApiPageRequest.from(s,c)); }
    @GetMapping("/self-development") public ApiPage<ApiResourceView> selfDevelopment(HttpServletRequest r, @RequestParam(name="cursor",required=false) String c, @RequestParam(name="pageSize",required=false) Integer s) { return backend.selfDevelopment(ApiControllerSupportBridge.context(r), ApiPageRequest.from(s,c)); }
    @GetMapping("/automations") public ApiPage<ApiResourceView> automations(HttpServletRequest r, @RequestParam(name="cursor",required=false) String c, @RequestParam(name="pageSize",required=false) Integer s) { return backend.automations(ApiControllerSupportBridge.context(r), ApiPageRequest.from(s,c)); }
    @GetMapping("/tools") public ApiPage<ApiResourceView> tools(HttpServletRequest r, @RequestParam(name="cursor",required=false) String c, @RequestParam(name="pageSize",required=false) Integer s) { return backend.tools(ApiControllerSupportBridge.context(r), ApiPageRequest.from(s,c)); }
    @GetMapping("/security") public ApiResourceView security(HttpServletRequest r) { return backend.security(ApiControllerSupportBridge.context(r)); }
    @GetMapping("/audit") public ApiPage<ApiResourceView> audit(HttpServletRequest r, @RequestParam(name="q",required=false,defaultValue="") String q, @RequestParam(name="cursor",required=false) String c, @RequestParam(name="pageSize",required=false) Integer s) { requireQuery(q); return backend.audit(ApiControllerSupportBridge.context(r), q, ApiPageRequest.from(s,c)); }

    private static void requireQuery(String query) {
        if (query == null || query.length() > 256 || query.indexOf('\0') >= 0) throw new IllegalArgumentException("query invalid");
    }
}
