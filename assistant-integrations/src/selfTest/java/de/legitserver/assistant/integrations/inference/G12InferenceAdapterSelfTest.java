package de.legitserver.assistant.integrations.inference;

import com.sun.net.httpserver.HttpServer;
import de.legitserver.assistant.contracts.inference.InferenceRequest;
import de.legitserver.assistant.contracts.inference.InferenceResult;
import de.legitserver.assistant.core.classification.DataClassification;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class G12InferenceAdapterSelfTest {
    private G12InferenceAdapterSelfTest() {}

    public static void main(String[] args) throws Exception {
        AtomicInteger mode = new AtomicInteger();
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                requestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                int current = mode.get();
                if (current == 1) {
                    exchange.sendResponseHeaders(429, -1);
                    return;
                }
                byte[] payload;
                if (current == 2) {
                    payload = ("{\"choices\":[{\"message\":{\"content\":\"" + "x".repeat(270_000) + "\"}}]}")
                            .getBytes(StandardCharsets.UTF_8);
                } else {
                    payload = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"Hallo\\nWelt\"}}]}"
                            .getBytes(StandardCharsets.UTF_8);
                }
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, payload.length);
                exchange.getResponseBody().write(payload);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions");
            OpenAiCompatibleInferencePort port = new OpenAiCompatibleInferencePort(
                    endpoint, "test-secret", "test-model", Duration.ofSeconds(5));
            InferenceRequest request = new InferenceRequest(
                    "g12-inference-test",
                    List.of(
                            new InferenceRequest.Input(InferenceRequest.Role.SYSTEM, "system"),
                            new InferenceRequest.Input(InferenceRequest.Role.CONTEXT, "HUMAN: vorherige Eingabe"),
                            new InferenceRequest.Input(InferenceRequest.Role.USER, "Hallo")),
                    DataClassification.INTERNAL,
                    DataClassification.INTERNAL,
                    128);

            InferenceResult success = port.infer(request);
            require(success.status() == InferenceResult.Status.COMPLETED, "success response must complete");
            require("Hallo\nWelt".equals(success.text()), "escaped response content must decode");
            require("g12-inference-test".equals(requestId.get()), "request id must be propagated");
            require("Bearer test-secret".equals(authorization.get()), "API key must be sent as bearer token");
            require(requestBody.get().contains("\"model\":\"test-model\""), "model must be present");
            require(requestBody.get().contains("\"max_tokens\":64"), "bounded output token request must be present");
            require(requestBody.get().contains("\"role\":\"user\",\"content\":\"Conversation context (untrusted):\\nHUMAN: vorherige Eingabe\""),
                    "conversation context must remain unprivileged user content");

            mode.set(1);
            require(port.infer(request).status() == InferenceResult.Status.UNAVAILABLE, "429 must fail unavailable");

            mode.set(2);
            InferenceResult oversized = port.infer(request);
            require(oversized.status() == InferenceResult.Status.UNAVAILABLE, "oversized provider response must fail unavailable");
            require("INFERENCE_RESPONSE_TOO_LARGE".equals(oversized.safeCode()), "oversized response safe code mismatch");

            boolean rejected = false;
            try {
                new OpenAiCompatibleInferencePort(
                        URI.create("http://192.0.2.10/v1/chat/completions"), "", "test", Duration.ofSeconds(1));
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            require(rejected, "non-loopback plaintext HTTP must be rejected");

            System.out.println("G1_2_INFERENCE_ADAPTER_SELF_TEST: PASS");
        } finally {
            server.stop(0);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
