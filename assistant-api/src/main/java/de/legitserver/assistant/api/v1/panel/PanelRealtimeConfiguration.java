package de.legitserver.assistant.api.v1.panel;

import de.legitserver.assistant.panel.realtime.PanelRealtimeHub;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PanelRealtimeConfiguration {
    @Bean
    @ConditionalOnMissingBean(PanelRealtimeHub.class)
    PanelRealtimeHub panelRealtimeHub(Clock clock) {
        return new PanelRealtimeHub(512, 256, clock);
    }
}
