package de.legitserver.assistant.api.v1.backend;

import de.legitserver.assistant.panel.security.PanelPipelineCredentialIssuer;
import de.legitserver.assistant.pipeline.RuntimeRequestPipeline;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Installs the Phase-15 pipeline backend only when every required production port exists; otherwise fail closed. */
@Configuration(proxyBeanMethods = false)
public class AssistantApiBackendConfiguration {
    @Bean
    @ConditionalOnMissingBean(AssistantApiBackend.class)
    AssistantApiBackend assistantApiBackend(
            ObjectProvider<RuntimeRequestPipeline> pipelineProvider,
            ObjectProvider<PanelPipelineCredentialIssuer> credentialProvider,
            ObjectProvider<PanelAssistantResolutionPort> assistantProvider,
            ObjectProvider<PanelPipelineOperationPolicyPort> policyProvider,
            ObjectProvider<PanelApiReadPort> readProvider,
            Clock clock) {
        RuntimeRequestPipeline pipeline = pipelineProvider.getIfAvailable();
        PanelPipelineCredentialIssuer credentials = credentialProvider.getIfAvailable();
        PanelAssistantResolutionPort assistants = assistantProvider.getIfAvailable();
        PanelPipelineOperationPolicyPort policies = policyProvider.getIfAvailable();
        PanelApiReadPort reads = readProvider.getIfAvailable();
        if (pipeline == null || credentials == null || assistants == null || policies == null || reads == null) {
            return new UnavailableAssistantApiBackend();
        }
        return new PipelineAssistantApiBackend(pipeline, credentials, assistants, policies, reads, clock);
    }
}
