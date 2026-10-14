package de.legitserver.assistant.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Executable host for the Legit Assistant Platform. */
@SpringBootApplication(scanBasePackages = "de.legitserver.assistant")
public final class LegitAssistantApplication {

    private LegitAssistantApplication() {}

    public static void main(String[] args) {
        SpringApplication.run(LegitAssistantApplication.class, args);
    }
}
