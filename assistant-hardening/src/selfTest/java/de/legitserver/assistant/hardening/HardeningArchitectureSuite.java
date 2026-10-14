package de.legitserver.assistant.hardening;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Static hot-path gates for queue bounds, forbidden execution primitives, and obvious JDBC N+1 patterns. */
final class HardeningArchitectureSuite {
    private HardeningArchitectureSuite() {}

    static void run(Path projectRoot) throws Exception {
        verifyNoJdbcQueryInsideLoops(projectRoot);
        verifyBoundedPrimitivesRemainExplicit(projectRoot);
        System.out.println("PHASE16_NO_N_PLUS_ONE_HOT_PATHS: PASS");
        System.out.println("PHASE16_BOUNDED_PRIMITIVES_SCAN: PASS");
    }

    private static void verifyNoJdbcQueryInsideLoops(Path projectRoot) throws Exception {
        Path root = projectRoot.resolve("assistant-integrations/src/main/java/de/legitserver/assistant/integrations");
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(value -> value.getFileName().toString().startsWith("Jdbc"))
                    .filter(value -> value.toString().endsWith("Repository.java"))
                    .toList()) {
                List<String> lines = Files.readAllLines(path);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (!startsLoop(line)) continue;
                    int braceDepth = braceDelta(line);
                    int end = i;
                    if (braceDepth <= 0 && !line.contains("{")) continue;
                    for (int j = i + 1; j < lines.size(); j++) {
                        String nested = lines.get(j);
                        braceDepth += braceDelta(nested);
                        if (containsJdbcExecution(nested)) {
                            violations.add(path.getFileName() + ":" + (j + 1) + " query execution inside loop starting line " + (i + 1));
                        }
                        end = j;
                        if (braceDepth <= 0) break;
                    }
                    i = Math.max(i, end);
                }
            }
        }
        if (!violations.isEmpty()) throw new IllegalStateException("Potential N+1 JDBC hot paths: " + violations);
    }

    private static void verifyBoundedPrimitivesRemainExplicit(Path projectRoot) throws Exception {
        String admission = Files.readString(projectRoot.resolve(
                "assistant-pipeline/src/main/java/de/legitserver/assistant/pipeline/control/BoundedAdmissionController.java"));
        require(admission.contains("maxQueued") && admission.contains("Semaphore") && admission.contains("BACKPRESSURE"),
                "admission controller must retain explicit active/queue bounds");
        String replay = Files.readString(projectRoot.resolve(
                "assistant-security/src/main/java/de/legitserver/assistant/security/authn/BoundedReplayProtector.java"));
        require(replay.contains("maxEntries") && replay.contains("seen.size() >= maxEntries"),
                "replay cache must retain maxEntries bound");
        String rate = Files.readString(projectRoot.resolve(
                "assistant-pipeline/src/main/java/de/legitserver/assistant/pipeline/policy/SlidingWindowRateLimiter.java"));
        require(rate.contains("maxBuckets") && rate.contains("buckets.size() >= maxBuckets"),
                "rate limiter must retain bounded bucket count");
        String telemetry = Files.readString(projectRoot.resolve(
                "assistant-supervisor/src/main/java/de/legitserver/assistant/supervisor/health/InMemoryAssistantOperationalTelemetry.java"));
        require(telemetry.contains("LATENCY_WINDOW") && telemetry.contains("FAILURE_WINDOW"),
                "supervisor telemetry must retain bounded history windows");
        String realtime = Files.readString(projectRoot.resolve(
                "assistant-panel/src/main/java/de/legitserver/assistant/panel/realtime/PanelRealtimeHub.java"));
        require(realtime.contains("historyCapacity") && realtime.contains("maxSubscribers"),
                "SSE realtime must retain history/subscriber bounds");
    }

    private static boolean startsLoop(String line) {
        return line.startsWith("for (") || line.startsWith("for(") || line.startsWith("while (") || line.startsWith("while(");
    }

    private static int braceDelta(String line) {
        int delta = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '{') delta++;
            else if (line.charAt(i) == '}') delta--;
        }
        return delta;
    }

    private static boolean containsJdbcExecution(String line) {
        String normalized = line.replace(" ", "");
        return normalized.contains("prepareStatement(")
                || normalized.contains("executeQuery(")
                || normalized.contains("executeUpdate(")
                || normalized.contains("executeBatch(");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Assertion failed: " + message);
    }
}
