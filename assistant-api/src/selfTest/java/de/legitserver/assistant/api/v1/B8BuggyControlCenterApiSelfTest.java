package de.legitserver.assistant.api.v1;

import de.legitserver.assistant.api.v1.buggy.BuggyControlCenterController;
import de.legitserver.assistant.api.v1.buggy.BuggyControlCenterEventPublisher;
import de.legitserver.assistant.buggy.controlcenter.BuggyControlCenterSurface;
import de.legitserver.assistant.panel.realtime.PanelRealtimeEventType;
import de.legitserver.assistant.panel.realtime.PanelRealtimeHub;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

/** Static + runtime B8 transport boundary verification without booting Spring. */
public final class B8BuggyControlCenterApiSelfTest {
    private B8BuggyControlCenterApiSelfTest() {}
    public static void main(String[] args) throws Exception {
        Path project = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        String controller = Files.readString(project.resolve("assistant-api/src/main/java/de/legitserver/assistant/api/v1/buggy/BuggyControlCenterController.java"));
        require(controller.contains("AssistantApiV1.BASE_PATH + \"/buggy\""), "same versioned API namespace");
        for (String route : new String[]{"/overview","/identity","/personality","/tasks","/activity","/approvals","/incidents","/knowledge","/self-development","/automations","/tools","/security","/audit"}) {
            require(controller.contains("@GetMapping(\"" + route + "\")"), "missing route " + route);
        }
        require(!controller.contains("@PostMapping"), "B8 Control Center backend must not add write bypasses");
        String secured = Files.readString(project.resolve("assistant-api/src/main/java/de/legitserver/assistant/api/v1/buggy/SecuredBuggyControlCenterBackend.java"));
        require(secured.contains("AssistantAuthorizationService"), "Foundation authorization reused");
        require(secured.contains("context.requirePanelRequest()"), "verified Panel identity required");
        require(!secured.contains("java.sql"), "API projection must not access DB directly");
        require(BuggyControlCenterSurface.values().length == 13, "all B8 surfaces have server-side capability mapping");

        String openApi = Files.readString(project.resolve("assistant-api/src/main/resources/openapi/buggy-control-center-v1.yaml"));
        require(openApi.contains("openapi: 3.1.0"), "B8 OpenAPI 3.1");
        require(openApi.contains("url: /api/assistant/v1/buggy"), "B8 OpenAPI server base");
        require(count(openApi, "operationId:") == 13, "B8 documents exactly 13 read surfaces");
        require(!openApi.contains("post:"), "B8 OpenAPI has no write bypass");

        PanelRealtimeHub hub = new PanelRealtimeHub(16, 4, Clock.fixed(Instant.parse("2026-10-05T00:20:00Z"), ZoneOffset.UTC));
        BuggyControlCenterEventPublisher events = new BuggyControlCenterEventPublisher(hub);
        events.publish(PanelRealtimeEventType.BUGGY_TASK_CHANGED, "task-1", Map.of("state", "ACTIVE"));
        require(hub.latestSequence() == 1, "Buggy event uses existing realtime hub");
        expectFailure(() -> events.publish(PanelRealtimeEventType.AUDIT_RECORDED, "audit-1", Map.of()));
        System.out.println("B8_BUGGY_CONTROL_CENTER_API_SELF_TEST: PASS");
    }
    private static void expectFailure(Runnable action) { try { action.run(); } catch (RuntimeException expected) { return; } throw new IllegalStateException("Expected failure"); }
    private static int count(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException("B8 API self-test failed: " + message); }
}
