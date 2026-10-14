package de.legitserver.assistant.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.context.ConfigurableApplicationContext;

/** Offline Phase-1 bootstrap and architecture verification. */
public final class Phase1SelfTest {

    private static final List<String> DOMAIN_MODULES =
            List.of(
                    "assistant-core",
                    "assistant-security",
                    "assistant-observability",
                    "assistant-conversations",
                    "assistant-knowledge",
                    "assistant-bugs",
                    "assistant-requests");

    private Phase1SelfTest() {}

    public static void main(String[] args) throws Exception {
        Path projectRoot = requireProjectRoot(args);
        verifyArchitecture(projectRoot);
        verifySpringBootstrap();
        System.out.println("PHASE1_SELF_TEST: PASS");
    }

    private static Path requireProjectRoot(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one project-root argument");
        }
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Project root does not exist: " + root);
        }
        return root;
    }

    private static void verifyArchitecture(Path projectRoot) throws IOException {
        List<String> violations = new ArrayList<>();
        for (String module : DOMAIN_MODULES) {
            Path javaRoot = projectRoot.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(javaRoot)) {
                continue;
            }
            try (var files = Files.walk(javaRoot)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> inspectDomainSource(path, violations));
            }
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Architecture violations:\n" + String.join("\n", violations));
        }
    }

    private static void inspectDomainSource(Path source, List<String> violations) {
        try {
            String text = Files.readString(source);
            if (text.contains("import org.springframework.")) {
                violations.add(source + ": domain module imports Spring");
            }
            if (text.contains("import de.legitserver.assistant.integrations.")) {
                violations.add(source + ": domain module imports integrations");
            }
            if (text.contains("import de.legitserver.assistant.persistence.")) {
                violations.add(source + ": domain module imports persistence infrastructure");
            }
            if (text.contains("import de.legitserver.assistant.api.")) {
                violations.add(source + ": domain module imports API adapters");
            }
            if (text.contains("import de.legitserver.assistant.bootstrap.")) {
                violations.add(source + ": domain module imports bootstrap");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect " + source, exception);
        }
    }

    private static void verifySpringBootstrap() {
        SpringApplication application = new SpringApplication(LegitAssistantApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context =
                application.run(
                        "--spring.main.banner-mode=off",
                        "--logging.level.root=ERROR",
                        "--management.endpoint.health.probes.enabled=true")) {
            HealthEndpoint endpoint = context.getBean(HealthEndpoint.class);
            if (endpoint == null) {
                throw new IllegalStateException("HealthEndpoint was not created");
            }
        }
    }
}
