package de.legitserver.assistant.api.v1.openapi;

import de.legitserver.assistant.api.v1.AssistantApiV1;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves the pinned Phase-14 OpenAPI document shipped with this runtime. */
@RestController
@RequestMapping(AssistantApiV1.BASE_PATH)
public final class OpenApiController {
    @GetMapping(value = "/openapi.yaml", produces = "application/yaml")
    public String openApi() throws IOException {
        try (InputStream input = OpenApiController.class.getResourceAsStream("/openapi/assistant-v1.yaml")) {
            if (input == null) throw new IOException("OpenAPI resource unavailable");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
