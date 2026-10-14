package de.legitserver.assistant.api.v1;

import de.legitserver.assistant.api.internal.networkcore.HmacNetworkCoreIngressAuthenticator;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.security.authn.BoundedReplayProtector;
import de.legitserver.assistant.security.authn.HmacServiceTokenService;
import de.legitserver.assistant.security.authn.ServiceScope;
import de.legitserver.assistant.security.authn.ServiceScopes;
import de.legitserver.assistant.security.authn.ServiceTokenClaims;
import java.time.Instant;
import java.util.Set;

public final class G12NetworkCoreIngressSecuritySelfTest {
    public static void main(String[] args) {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) key[i] = (byte) (i + 11);
        HmacServiceTokenService tokens = new HmacServiceTokenService(key, new BoundedReplayProtector(64));
        HmacNetworkCoreIngressAuthenticator auth = new HmacNetworkCoreIngressAuthenticator(tokens);
        RequestId requestId = new RequestId("11111111-2222-3333-4444-555555555555");
        Instant now = Instant.parse("2026-10-05T18:00:00Z");
        String token = tokens.issue(new ServiceTokenClaims(
                "networkcore", "legit-assistant", Set.of(ServiceScopes.INVOKE), now, now.plusSeconds(60),
                "g12-networkcore-jti", requestId));
        require(auth.authenticate("Bearer " + token, requestId, now.plusSeconds(1)).authenticated(), "valid NetworkCore token must authenticate");

        HmacServiceTokenService mismatchTokens = new HmacServiceTokenService(key, new BoundedReplayProtector(64));
        String mismatch = mismatchTokens.issue(new ServiceTokenClaims(
                "networkcore", "other-audience", Set.of(new ServiceScope("assistant.invoke")), now, now.plusSeconds(60),
                "g12-networkcore-jti-2", requestId));
        require(!new HmacNetworkCoreIngressAuthenticator(mismatchTokens)
                .authenticate("Bearer " + mismatch, requestId, now.plusSeconds(1)).authenticated(), "audience mismatch must fail");
        System.out.println("G1_2_NETWORKCORE_INGRESS_SECURITY_SELF_TEST: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
