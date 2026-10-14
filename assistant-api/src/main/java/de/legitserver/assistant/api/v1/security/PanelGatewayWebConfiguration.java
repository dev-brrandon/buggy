package de.legitserver.assistant.api.v1.security;

import de.legitserver.assistant.api.v1.AssistantApiV1;
import de.legitserver.assistant.panel.security.PanelGatewayAuthenticationPort;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Phase-15 HTTP ingress security. OpenAPI stays readable; operational API routes require signed Panel requests. */
@Configuration(proxyBeanMethods = false)
public class PanelGatewayWebConfiguration implements WebMvcConfigurer {
    private final PanelGatewayAuthenticationPort authentication;
    private final Clock clock;

    public PanelGatewayWebConfiguration(PanelGatewayAuthenticationPort authentication, Clock clock) {
        this.authentication = authentication;
        this.clock = clock;
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    static Clock systemClock() {
        return Clock.systemUTC();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new PanelGatewayInterceptor(authentication, clock))
                .addPathPatterns(AssistantApiV1.BASE_PATH + "/**")
                .excludePathPatterns(AssistantApiV1.BASE_PATH + "/openapi.yaml");
    }
}
