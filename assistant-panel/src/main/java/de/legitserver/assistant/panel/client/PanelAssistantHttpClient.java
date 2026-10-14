package de.legitserver.assistant.panel.client;

import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.panel.security.PanelGatewayRequestSigner;
import de.legitserver.assistant.panel.security.PanelHeaders;
import de.legitserver.assistant.panel.security.PanelRequestIdentity;
import de.legitserver.assistant.panel.security.PanelSignedHeaders;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Reference Panel Backend HTTP-only adapter. It contains no database integration by design. */
public final class PanelAssistantHttpClient {
    private final URI baseUri;
    private final HttpClient client;
    private final PanelGatewayRequestSigner signer;
    private final Duration timeout;

    public PanelAssistantHttpClient(URI baseUri, HttpClient client, PanelGatewayRequestSigner signer, Duration timeout) {
        if (baseUri == null || client == null || signer == null || timeout == null
                || timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalArgumentException("invalid Panel HTTP client configuration");
        }
        if (!"https".equalsIgnoreCase(baseUri.getScheme()) && !isLoopbackHttp(baseUri)) {
            throw new IllegalArgumentException("Panel Assistant API must use HTTPS outside loopback tests");
        }
        this.baseUri = baseUri;
        this.client = client;
        this.signer = signer;
        this.timeout = timeout;
    }

    public PanelHttpResponse execute(
            PanelRequestIdentity identity,
            RequestId requestId,
            TraceId traceId,
            PanelHttpRequest request) throws IOException, InterruptedException {
        if (identity == null || requestId == null || traceId == null || request == null) {
            throw new IllegalArgumentException("Panel request fields required");
        }
        PanelSignedHeaders headers = signer.sign(identity, requestId, traceId);
        URI target = baseUri.resolve(request.relativePath());
        if (!sameAuthority(baseUri, target)) throw new IllegalArgumentException("relativePath escaped configured Assistant authority");
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Authorization", headers.authorization())
                .header(PanelHeaders.USER_CONTEXT, headers.userContext())
                .header(PanelHeaders.REQUEST_ID, requestId.value())
                .header(PanelHeaders.TRACE_ID, traceId.value());
        if (request.method().equals("POST")) {
            builder.header("Content-Type", "application/json")
                    .header(PanelHeaders.IDEMPOTENCY_KEY, request.idempotencyKey())
                    .POST(HttpRequest.BodyPublishers.ofString(request.jsonBody()));
        } else {
            builder.GET();
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new PanelHttpResponse(response.statusCode(), response.body(), response.headers().map());
    }

    private static boolean sameAuthority(URI base, URI target) {
        return java.util.Objects.equals(base.getScheme(), target.getScheme())
                && java.util.Objects.equals(base.getHost(), target.getHost())
                && effectivePort(base) == effectivePort(target);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static boolean isLoopbackHttp(URI uri) {
        if (!"http".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
    }
}
