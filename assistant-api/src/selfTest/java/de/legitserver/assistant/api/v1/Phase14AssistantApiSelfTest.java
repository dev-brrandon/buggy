package de.legitserver.assistant.api.v1;

import de.legitserver.assistant.api.v1.backend.UnavailableAssistantApiBackend;
import de.legitserver.assistant.api.v1.error.ApiErrorEnvelope;
import de.legitserver.assistant.api.v1.error.ApiException;
import de.legitserver.assistant.api.v1.model.ApiCallContext;
import de.legitserver.assistant.api.v1.pagination.ApiPage;
import de.legitserver.assistant.api.v1.pagination.ApiPageRequest;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Dependency-light Phase-14 contract and security regression test. */
public final class Phase14AssistantApiSelfTest {
    private static final List<String> REQUIRED_PATHS = List.of(
            "/assistants:",
            "/assistants/{id}:",
            "/conversations:",
            "/conversations/{id}:",
            "/conversations/{id}/messages:",
            "/conversations/{id}/cancel:",
            "/bugs:",
            "/bugs/{id}:",
            "/bugs/{id}/status:",
            "/bugs/{id}/relations:",
            "/knowledge/search:",
            "/knowledge/documents/{id}:",
            "/requests:",
            "/requests/{id}:",
            "/requests/{id}/approve:",
            "/requests/{id}/deny:",
            "/requests/{id}/cancel:",
            "/supervisor/assistants:",
            "/supervisor/assistants/{id}/health:",
            "/supervisor/assistants/{id}/failures:");

    private Phase14AssistantApiSelfTest() {}

    public static void main(String[] args) throws Exception {
        require(args.length == 1, "project dir required");
        Path project = Path.of(args[0]);
        verifyVersioning();
        verifyPagination();
        verifyUnifiedErrorModel();
        verifyFailClosedDefaultBackend();
        verifyOpenApi(project);
        verifyForbiddenBypassScan(project);
        System.out.println("PHASE14_ASSISTANT_API_SELF_TEST: PASS");
    }

    private static void verifyVersioning() {
        require("/api/assistant/v1".equals(AssistantApiV1.BASE_PATH), "stable v1 namespace");
        require(AssistantApiV1.MAX_PAGE_SIZE == 100, "bounded page size");
    }

    private static void verifyPagination() {
        ApiPageRequest first = ApiPageRequest.from(2, null);
        ApiPage<String> page1 = ApiPage.slice(List.of("a", "b", "c"), first);
        require(page1.items().equals(List.of("a", "b")), "first page items");
        require(page1.hasMore() && page1.nextCursor() != null, "first page cursor");
        ApiPage<String> page2 = ApiPage.slice(List.of("a", "b", "c"), ApiPageRequest.from(2, page1.nextCursor()));
        require(page2.items().equals(List.of("c")) && !page2.hasMore(), "second page items");
        expectFailure(() -> ApiPageRequest.from(101, null));
        expectFailure(() -> ApiPageRequest.from(10, "not-a-valid-cursor"));
    }

    private static void verifyUnifiedErrorModel() {
        ApiErrorEnvelope error = new ApiErrorEnvelope(
                "API.INVALID_ARGUMENT", "safe message", "req-1", false, Map.of("field", "pageSize"));
        require("req-1".equals(error.requestId()), "error request correlation");
        require(!error.retryable(), "error retryability explicit");
    }

    private static void verifyFailClosedDefaultBackend() {
        UnavailableAssistantApiBackend backend = new UnavailableAssistantApiBackend();
        ApiCallContext context = new ApiCallContext(new RequestId("req-1"), new TraceId("trace-1"), "Bearer token");
        try {
            backend.listAssistants(context, ApiPageRequest.from(null, null));
            throw new IllegalStateException("unconfigured backend must fail closed");
        } catch (ApiException expected) {
            require(expected.status() == HttpStatus.SERVICE_UNAVAILABLE, "default backend returns 503");
            require("API.BACKEND_UNAVAILABLE".equals(expected.errorCode()), "stable unavailable code");
        }
    }

    private static void verifyOpenApi(Path project) throws Exception {
        Path specPath = project.resolve("assistant-api/src/main/resources/openapi/assistant-v1.yaml");
        String spec = Files.readString(specPath);
        require(spec.contains("openapi: 3.1.0"), "OpenAPI 3.1");
        require(spec.contains("url: /api/assistant/v1"), "versioned server base");
        for (String path : REQUIRED_PATHS) require(spec.contains("  " + path), "missing path " + path);
        require(count(spec, "operationId:") >= 21, "all Phase-14 core operations remain documented");
        require(count(spec, "#/components/parameters/IdempotencyKey") == 10,
                "all 10 mutating POSTs document idempotency");
        require(count(spec, "#/components/parameters/PageSize") >= 5,
                "all Phase-14 collection/search endpoints document pageSize");
        require(count(spec, "#/components/parameters/Cursor") >= 5,
                "all Phase-14 collection/search endpoints document cursor");
        require(spec.contains("ApiError:"), "unified API error schema");
        require(spec.contains("process-local"), "V1 idempotency scope documented honestly");
    }

    private static void verifyForbiddenBypassScan(Path project) throws Exception {
        Path src = project.resolve("assistant-api/src/main/java");
        StringBuilder combined = new StringBuilder();
        try (var files = Files.walk(src)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            combined.append(Files.readString(path)).append('\n');
                        } catch (java.io.IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    });
        }
        String text = combined.toString();
        require(!text.contains("ProcessBuilder"), "no ProcessBuilder API bypass");
        require(!text.contains("Runtime.getRuntime().exec"), "no runtime exec API bypass");
        require(!text.contains("SecurityAuthority"), "no parallel API security authority");
    }

    private static int count(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static void expectFailure(Runnable runnable) {
        try {
            runnable.run();
            throw new IllegalStateException("expected failure");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
