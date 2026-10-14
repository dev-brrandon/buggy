package de.legitserver.assistant.bootstrap.panel;

import de.legitserver.assistant.api.internal.networkcore.HmacNetworkCoreIngressAuthenticator;
import de.legitserver.assistant.api.internal.networkcore.NetworkCoreIngressAuthenticationPort;
import de.legitserver.assistant.api.internal.networkcore.UnavailableNetworkCoreIngressAuthenticator;
import de.legitserver.assistant.security.authn.BoundedReplayProtector;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import java.util.Base64;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** G1 private NetworkCore ingress security. Missing secret keeps Minecraft ingress unavailable. */
@Configuration(proxyBeanMethods = false)
public class G1NetworkCoreIngressConfiguration {
    public static final String NETWORKCORE_KEY_ENV = "LEGIT_ASSISTANT_NETWORKCORE_HMAC_KEY_B64";

    @Bean
    NetworkCoreIngressAuthenticationPort networkCoreIngressAuthenticationPort(Environment environment) {
        byte[] key = decode(environment.getProperty(NETWORKCORE_KEY_ENV));
        if (key == null || key.length < 32) return new UnavailableNetworkCoreIngressAuthenticator();
        return new HmacNetworkCoreIngressAuthenticator(new HmacServiceTokenService(key, new BoundedReplayProtector(8192)));
    }

    private static byte[] decode(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Base64.getDecoder().decode(value.trim()); }
        catch (IllegalArgumentException malformed) { return null; }
    }
}
