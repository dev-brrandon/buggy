package de.legitserver.assistant.panel.client;

import de.legitserver.assistant.core.validation.CoreValidation;

/** Bounded HTTP request emitted by the reference Panel Backend client. */
public record PanelHttpRequest(String method, String relativePath, String jsonBody, String idempotencyKey) {
    public PanelHttpRequest {
        method = CoreValidation.requireSafeToken(method, "method", 8).toUpperCase(java.util.Locale.ROOT);
        if (!method.equals("GET") && !method.equals("POST")) throw new IllegalArgumentException("only GET and POST are supported");
        if (relativePath == null || relativePath.isBlank() || relativePath.length() > 1024 || !relativePath.startsWith("/")) {
            throw new IllegalArgumentException("relativePath invalid");
        }
        if (relativePath.contains("..") || relativePath.contains("\\") || relativePath.startsWith("//")) {
            throw new IllegalArgumentException("relativePath traversal/authority escape denied");
        }
        jsonBody = jsonBody == null ? "" : jsonBody;
        if (jsonBody.length() > 1_048_576) throw new IllegalArgumentException("jsonBody exceeds 1 MiB");
        idempotencyKey = CoreValidation.optionalSafeToken(idempotencyKey, "idempotencyKey", 128);
        if (method.equals("POST") && idempotencyKey == null) throw new IllegalArgumentException("POST requires idempotencyKey");
        if (method.equals("GET") && !jsonBody.isEmpty()) throw new IllegalArgumentException("GET body not supported");
    }
}
