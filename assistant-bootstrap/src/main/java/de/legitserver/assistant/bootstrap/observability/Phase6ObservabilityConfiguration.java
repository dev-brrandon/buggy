package de.legitserver.assistant.bootstrap.observability;

import de.legitserver.assistant.observability.audit.AuditPort;
import de.legitserver.assistant.observability.audit.AuditRecorder;
import de.legitserver.assistant.observability.audit.UnavailableAuditPort;
import de.legitserver.assistant.observability.health.AssistantHealthService;
import de.legitserver.assistant.observability.health.DependencyHealthRegistry;
import de.legitserver.assistant.observability.logging.SanitizedStructuredLogger;
import de.legitserver.assistant.observability.logging.StructuredLogSink;
import de.legitserver.assistant.observability.logging.SystemStructuredLogSink;
import de.legitserver.assistant.observability.logging.TelemetrySanitizer;
import de.legitserver.assistant.observability.metrics.AssistantMetrics;
import de.legitserver.assistant.observability.metrics.InMemoryAssistantMetrics;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Phase-6 runtime wiring with fail-safe defaults and no external dependency assumptions. */
@Configuration(proxyBeanMethods = false)
public class Phase6ObservabilityConfiguration {

    @Bean
    public TelemetrySanitizer telemetrySanitizer() {
        return TelemetrySanitizer.standard();
    }

    @Bean
    public StructuredLogSink structuredLogSink() {
        return new SystemStructuredLogSink("de.legitserver.assistant.structured");
    }

    @Bean
    public SanitizedStructuredLogger sanitizedStructuredLogger(
            TelemetrySanitizer sanitizer, StructuredLogSink sink) {
        return new SanitizedStructuredLogger(sanitizer, sink);
    }

    @Bean
    public AssistantMetrics assistantMetrics() {
        return new InMemoryAssistantMetrics();
    }

    @Bean
    public DependencyHealthRegistry dependencyHealthRegistry() {
        return new DependencyHealthRegistry();
    }

    @Bean
    public AssistantHealthService assistantHealthService(DependencyHealthRegistry registry) {
        return new AssistantHealthService(registry);
    }

    @Bean
    @ConditionalOnMissingBean(AuditPort.class)
    public AuditPort unavailableAuditPort() {
        return new UnavailableAuditPort();
    }

    @Bean
    public AuditRecorder auditRecorder(AuditPort auditPort) {
        return new AuditRecorder(auditPort);
    }

    @Bean
    public Phase6MetricsBinder phase6MetricsBinder(AssistantMetrics metrics, AssistantHealthService health) {
        return new Phase6MetricsBinder(metrics, health);
    }

    @Bean
    public Phase6DependencyHealthIndicator phase6Dependencies(AssistantHealthService health) {
        return new Phase6DependencyHealthIndicator(health);
    }
}
