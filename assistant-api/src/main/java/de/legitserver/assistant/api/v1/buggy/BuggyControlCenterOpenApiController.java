package de.legitserver.assistant.api.v1.buggy;

import de.legitserver.assistant.api.v1.AssistantApiV1;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves the pinned B8 Buggy Control Center OpenAPI contract under the signed Panel/API boundary. */
@RestController
@RequestMapping(AssistantApiV1.BASE_PATH + "/buggy")
public final class BuggyControlCenterOpenApiController {
    @GetMapping(value = "/openapi.yaml", produces = "application/yaml")
    public String openApi() throws IOException {
        try (InputStream input = BuggyControlCenterOpenApiController.class
                .getResourceAsStream("/openapi/buggy-control-center-v1.yaml")) {
            if (input == null) throw new IOException("Buggy Control Center OpenAPI resource unavailable");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
