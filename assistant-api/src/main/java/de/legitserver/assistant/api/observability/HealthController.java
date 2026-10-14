package de.legitserver.assistant.api.observability;

import de.legitserver.assistant.observability.health.AssistantHealthService;
import de.legitserver.assistant.observability.health.AssistantHealthSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Minimal public health surface. Detailed dependency health stays on the local management surface. */
@RestController
public final class HealthController {
    private final AssistantHealthService health;

    public HealthController(AssistantHealthService health) {
        if (health == null) throw new IllegalArgumentException("health must not be null");
        this.health = health;
    }

    @GetMapping("/health/live")
    public Map<String, Object> live() {
        AssistantHealthSnapshot snapshot = health.snapshot();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", snapshot.live() ? "UP" : "DOWN");
        body.put("live", snapshot.live());
        body.put("capturedAt", snapshot.capturedAt().toString());
        return Map.copyOf(body);
    }

    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        AssistantHealthSnapshot snapshot = health.snapshot();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", snapshot.status().name());
        body.put("ready", snapshot.ready());
        body.put("capturedAt", snapshot.capturedAt().toString());
        HttpStatus status = snapshot.ready() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(Map.copyOf(body));
    }
}
