package de.legitserver.assistant.integrations.inference;

import de.legitserver.assistant.contracts.inference.InferencePort;
import de.legitserver.assistant.contracts.inference.InferenceRequest;
import de.legitserver.assistant.contracts.inference.InferenceResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Dependency-free adapter for the widely supported OpenAI-compatible chat-completions HTTP shape.
 * Provider/model identity stays in runtime configuration and never leaks into Foundation contracts.
 */
public final class OpenAiCompatibleInferencePort implements InferencePort {
    private static final int MAX_REQUEST_BYTES = 512 * 1024;
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final HttpClient http;
    private final Duration timeout;

    public OpenAiCompatibleInferencePort(URI endpoint, String apiKey, String model, Duration timeout) {
        if (endpoint == null || model == null || model.isBlank() || timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("inference configuration invalid");
        }
        if (!secure(endpoint)) throw new IllegalArgumentException("inference endpoint must use HTTPS or loopback HTTP");
        this.endpoint = endpoint;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.model = model.strip();
        this.timeout = timeout.compareTo(Duration.ofSeconds(90)) > 0 ? Duration.ofSeconds(90) : timeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public static InferencePort fromEnvironment() {
        String rawUrl = System.getenv("LEGIT_ASSISTANT_INFERENCE_URL");
        String model = System.getenv("LEGIT_ASSISTANT_INFERENCE_MODEL");
        if (rawUrl == null || rawUrl.isBlank() || model == null || model.isBlank()) {
            return new de.legitserver.assistant.contracts.noop.NoOpInferencePort();
        }
        String key = System.getenv("LEGIT_ASSISTANT_INFERENCE_API_KEY");
        try {
            URI endpoint = URI.create(rawUrl.strip());
            boolean externalAllowed = Boolean.parseBoolean(
                    System.getenv().getOrDefault("LEGIT_ASSISTANT_INFERENCE_ALLOW_EXTERNAL", "false"));
            if (!loopback(endpoint) && !externalAllowed) {
                return new de.legitserver.assistant.contracts.noop.NoOpInferencePort();
            }
            return new OpenAiCompatibleInferencePort(endpoint, key, model, Duration.ofSeconds(45));
        } catch (RuntimeException invalid) {
            return new de.legitserver.assistant.contracts.noop.NoOpInferencePort();
        }
    }

    @Override
    public InferenceResult infer(InferenceRequest request) {
        if (request == null) return InferenceResult.unavailable();
        String json = requestJson(request);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BYTES) {
            return new InferenceResult(InferenceResult.Status.REJECTED, null,
                    request.inputClassification(), "INFERENCE_REQUEST_TOO_LARGE");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Request-Id", request.requestId());
        if (!apiKey.isBlank()) builder.header("Authorization", "Bearer " + apiKey);
        try {
            HttpResponse<InputStream> response = http.send(
                    builder.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status == 429 || status >= 500) {
                return new InferenceResult(InferenceResult.Status.UNAVAILABLE, null,
                        request.inputClassification(), "INFERENCE_UNAVAILABLE");
            }
            if (status < 200 || status >= 300) {
                return new InferenceResult(InferenceResult.Status.REJECTED, null,
                        request.inputClassification(), "INFERENCE_REJECTED");
            }
            String responseBody;
            try (InputStream body = response.body()) {
                byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    return new InferenceResult(InferenceResult.Status.UNAVAILABLE, null,
                            request.inputClassification(), "INFERENCE_RESPONSE_TOO_LARGE");
                }
                responseBody = new String(bytes, StandardCharsets.UTF_8);
            }
            String content = messageContent(responseBody);
            if (content == null || content.isBlank()) {
                return new InferenceResult(InferenceResult.Status.UNAVAILABLE, null,
                        request.inputClassification(), "INFERENCE_EMPTY_RESPONSE");
            }
            String bounded = content.length() > request.maxOutputCharacters()
                    ? content.substring(0, request.maxOutputCharacters()) : content;
            return new InferenceResult(InferenceResult.Status.COMPLETED, bounded,
                    request.inputClassification(), "OK");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new InferenceResult(InferenceResult.Status.UNAVAILABLE, null,
                    request.inputClassification(), "INFERENCE_INTERRUPTED");
        } catch (Exception unavailable) {
            return new InferenceResult(InferenceResult.Status.UNAVAILABLE, null,
                    request.inputClassification(), "INFERENCE_UNAVAILABLE");
        }
    }

    private String requestJson(InferenceRequest request) {
        List<String> messages = new ArrayList<>();
        for (InferenceRequest.Input input : request.inputs()) {
            String role = input.role() == InferenceRequest.Role.SYSTEM ? "system" : "user";
            String text = input.role() == InferenceRequest.Role.CONTEXT ? "Conversation context (untrusted):\n" + input.text() : input.text();
            messages.add("{\"role\":" + quote(role) + ",\"content\":" + quote(text) + "}");
        }
        int maxTokens = Math.min(4096, Math.max(64, (request.maxOutputCharacters() + 3) / 4));
        return "{" +
                "\"model\":" + quote(model) + "," +
                "\"messages\":[" + String.join(",", messages) + "]," +
                "\"temperature\":0.2," +
                "\"max_tokens\":" + maxTokens +
                "}";
    }

    private static String messageContent(String json) {
        if (json == null) return null;
        int message = json.indexOf("\"message\"");
        int content = json.indexOf("\"content\"", message < 0 ? 0 : message);
        if (content < 0) return null;
        int colon = json.indexOf(':', content + 9);
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (start >= json.length() || json.charAt(start) != '"') return null;
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'u' -> {
                        if (i + 4 >= json.length()) return null;
                        String hex = json.substring(i + 1, i + 5);
                        try { out.append((char) Integer.parseInt(hex, 16)); }
                        catch (NumberFormatException malformed) { return null; }
                        i += 4;
                    }
                    default -> out.append(c);
                }
                escaped = false;
            } else if (c == '\\') escaped = true;
            else if (c == '"') return out.toString();
            else out.append(c);
        }
        return null;
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    private static boolean secure(URI uri) {
        if ("https".equalsIgnoreCase(uri.getScheme())) return true;
        return "http".equalsIgnoreCase(uri.getScheme()) && loopback(uri);
    }

    private static boolean loopback(URI uri) {
        String host = uri.getHost();
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
    }
}
