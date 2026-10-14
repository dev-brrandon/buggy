package de.legitserver.assistant.panel;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** B9 static Control Center frontend contract/security verification. */
public final class B9BuggyControlCenterFrontendSelfTest {
    private static final String BASE = "META-INF/legit-panel/buggy-control-center/";

    private B9BuggyControlCenterFrontendSelfTest() {}

    public static void main(String[] args) throws Exception {
        String html = read("index.html");
        String css = read("assets/control-center.css");
        String js = read("assets/control-center.js");
        String manifest = read("manifest.json");
        String bridge = read("PANEL-BACKEND-BRIDGE.md");

        verifyPlanesAndNavigation(html, js, manifest);
        verifyEmergencyBoundary(html, js, manifest);
        verifyBrowserTransportBoundary(js, manifest, bridge);
        verifyRenderingSafety(html, js);
        verifyContextAwareChat(js);
        verifyResponsiveAccessibleShell(html, css);
        System.out.println("B9_BUGGY_CONTROL_CENTER_FRONTEND_SELF_TEST: PASS");
    }

    private static void verifyPlanesAndNavigation(String html, String js, String manifest) {
        for (String plane : List.of("COMMAND", "OBSERVABILITY", "EVOLUTION")) {
            require(js.contains(plane), "missing plane in JS: " + plane);
            require(manifest.contains("\"" + plane + "\""), "missing plane in manifest: " + plane);
        }
        for (String surface : List.of(
                "overview", "chat", "activity", "tasks", "approvals", "incidents", "bugs", "knowledge",
                "self-development", "automations", "tools", "assistants", "infrastructure", "deployments",
                "security", "personality", "settings", "audit")) {
            require(js.contains("\"" + surface + "\""), "missing navigation surface: " + surface);
        }
        require(html.contains("Buggy Control Center"), "Control Center title missing");
        require(manifest.contains("buggy-control-center-v1"), "frontend manifest id missing");
        require(manifest.contains("\"directAssistantApi\": false"), "manifest must deny direct Assistant API");
    }

    private static void verifyEmergencyBoundary(String html, String js, String manifest) {
        for (String operation : List.of(
                "buggy.emergency.pause",
                "buggy.emergency.read-only",
                "buggy.emergency.disable-automations",
                "buggy.emergency.disable-self-healing",
                "buggy.emergency.disable-tools",
                "buggy.emergency.maintenance")) {
            require(html.contains(operation), "emergency button missing: " + operation);
            require(js.contains(operation), "emergency operation missing: " + operation);
            require(manifest.contains(operation), "manifest emergency operation missing: " + operation);
        }
        require(html.contains("FOUNDATION CONTROL"), "emergency controls must be visually Foundation-owned");
        require(html.contains("NetworkCore"), "NetworkCore authority statement missing");
        require(js.contains("state.bridge.command(operation"), "emergency controls must use Panel Backend command bridge");
        require(manifest.contains("\"authority\": \"FOUNDATION_OWNED\""), "manifest authority boundary missing");
        require(manifest.contains("\"auditMutable\": false"), "audit must be immutable in frontend manifest");
        require(manifest.contains("\"identityCoreMutable\": false"), "Identity Core must be immutable in frontend manifest");
    }

    private static void verifyBrowserTransportBoundary(String js, String manifest, String bridge) {
        for (String forbidden : List.of(
                "/api/assistant/v1", "fetch(", "XMLHttpRequest", "EventSource", "WebSocket", "document.cookie",
                "localStorage", "sessionStorage")) {
            require(!js.contains(forbidden), "frontend must not contain direct/credential browser transport primitive: " + forbidden);
        }
        require(js.contains("window.LegitPanelAssistantBridge?.buggy"), "trusted Panel Backend bridge lookup missing");
        require(js.contains("direct API fallback is permitted") || js.contains("No direct API fallback is permitted"),
                "fail-closed bridge message missing");
        require(manifest.contains("PANEL_BACKEND_BRIDGE_ONLY"), "bridge-only transport manifest missing");
        require(bridge.contains("Browser → Panel Backend") || bridge.contains("browser bundle, not a security boundary"),
                "bridge contract must state browser trust boundary");
        require(bridge.contains("Phase-13/Phase-15"), "bridge command path must retain pipeline contracts");
    }

    private static void verifyRenderingSafety(String html, String js) {
        for (String forbidden : List.of("innerHTML", "outerHTML", "insertAdjacentHTML", "eval(", "new Function")) {
            require(!js.contains(forbidden), "unsafe dynamic rendering primitive present: " + forbidden);
        }
        require(js.contains("textContent"), "safe text rendering must use textContent");
        require(!html.contains("onclick="), "inline click handlers are not permitted");
        require(!html.contains("onload="), "inline load handlers are not permitted");
        require(html.contains("type=\"module\""), "frontend JavaScript must load as module");
    }

    private static void verifyContextAwareChat(String js) {
        require(js.contains("contextType"), "contextType deep-link handling missing");
        require(js.contains("contextId"), "contextId deep-link handling missing");
        require(js.contains("openChat(context"), "context-aware chat handoff missing");
        require(js.contains("bridge.openChat"), "Panel-host Chat bridge missing");
        require(js.contains("cleanToken"), "deep-link context must be token validated");
    }

    private static void verifyResponsiveAccessibleShell(String html, String css) {
        require(html.contains("Skip to content"), "skip link missing");
        require(html.contains("aria-live=\"polite\""), "live region missing");
        require(html.contains("<dialog"), "deliberate emergency confirmation dialog missing");
        require(css.contains("@media (max-width: 820px)"), "tablet responsive breakpoint missing");
        require(css.contains("@media (max-width: 560px)"), "mobile responsive breakpoint missing");
        require(css.contains("prefers-reduced-motion"), "reduced-motion accessibility handling missing");
    }

    private static String read(String relativePath) throws IOException {
        String resource = BASE + relativePath;
        try (InputStream input = B9BuggyControlCenterFrontendSelfTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IOException("missing frontend resource: " + resource);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
