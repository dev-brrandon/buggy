package de.legitserver.assistant.api.v1.panel;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PanelFoundationApiConfiguration {
    @Bean
    @ConditionalOnMissingBean(PanelFoundationApiBackend.class)
    PanelFoundationApiBackend unavailablePanelFoundationApiBackend(Clock clock) {
        return new UnavailablePanelFoundationApiBackend(clock);
    }
}
