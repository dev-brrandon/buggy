package de.legitserver.assistant.bootstrap.panel;

import de.legitserver.assistant.panel.security.HmacPanelGatewayAuthenticator;
import de.legitserver.assistant.panel.security.HmacPanelUserContextService;
import de.legitserver.assistant.panel.security.PanelGatewayAuthenticationPort;
import de.legitserver.assistant.panel.security.UnavailablePanelGatewayAuthenticator;
import de.legitserver.assistant.security.authn.BoundedReplayProtector;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import java.util.Base64;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Runtime gateway security. Missing or malformed secrets keep Panel API access fail-closed. */
@Configuration(proxyBeanMethods = false)
public class Phase15PanelGatewayConfiguration {
    public static final String SERVICE_KEY_ENV = "LEGIT_ASSISTANT_PANEL_SERVICE_HMAC_KEY_B64";
    public static final String CONTEXT_KEY_ENV = "LEGIT_ASSISTANT_PANEL_CONTEXT_HMAC_KEY_B64";

    @Bean
    PanelGatewayAuthenticationPort panelGatewayAuthenticationPort(Environment environment) {
        byte[] serviceKey = decode(environment.getProperty(SERVICE_KEY_ENV));
        byte[] contextKey = decode(environment.getProperty(CONTEXT_KEY_ENV));
        if (serviceKey == null || contextKey == null || serviceKey.length < 32 || contextKey.length < 32) {
            return new UnavailablePanelGatewayAuthenticator();
        }
        return new HmacPanelGatewayAuthenticator(
                new HmacServiceTokenService(serviceKey, new BoundedReplayProtector(8192)),
                new HmacPanelUserContextService(contextKey, new BoundedReplayProtector(8192)));
    }

    private static byte[] decode(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Base64.getDecoder().decode(value.trim());
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
