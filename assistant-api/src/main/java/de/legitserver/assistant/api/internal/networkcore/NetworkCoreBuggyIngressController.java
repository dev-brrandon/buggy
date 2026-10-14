package de.legitserver.assistant.api.internal.networkcore;

import de.legitserver.assistant.buggy.channel.BuggyChannelExecutionPort;
import de.legitserver.assistant.buggy.channel.BuggyChannelIngress;
import de.legitserver.assistant.buggy.channel.BuggyChannelPrincipal;
import de.legitserver.assistant.buggy.channel.BuggyChannelReply;
import de.legitserver.assistant.buggy.runtime.g1.BuggyChannelExecutionException;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.security.authn.ServiceAuthenticationResult;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * G1.2 private NetworkCore ingress. The service call is authenticated here; the canonical runtime
 * independently re-resolves actor identity and current Buggy permissions from NetworkCore before execution.
 */
@RestController
@RequestMapping("/internal/networkcore/v1/buggy")
public final class NetworkCoreBuggyIngressController {
    private final NetworkCoreIngressAuthenticationPort authentication;
    private final BuggyChannelIngress ingress;
    private final ObjectProvider<BuggyChannelExecutionPort> execution;
    private final Clock clock;

    public NetworkCoreBuggyIngressController(
            NetworkCoreIngressAuthenticationPort authentication,
            ObjectProvider<BuggyChannelExecutionPort> execution,
            Clock clock) {
        if (authentication == null || execution == null || clock == null) throw new IllegalArgumentException("dependencies required");
        this.authentication = authentication;
        this.execution = execution;
        this.clock = clock;
        this.ingress = new BuggyChannelIngress(clock);
    }

    @PostMapping("/messages")
    public ResponseEntity<?> message(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader("X-Request-Id") String requestIdHeader,
            @RequestBody MinecraftMessage body) {
        RequestId requestId;
        try { requestId = new RequestId(requestIdHeader); }
        catch (RuntimeException invalid) { return ResponseEntity.badRequest().body(Map.of("ok", false, "safeCode", "REQUEST_ID_INVALID")); }
        ServiceAuthenticationResult auth = authentication.authenticate(authorization, requestId, clock.instant());
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("ok", false, "safeCode", "NETWORKCORE_INGRESS_SECURITY_UNAVAILABLE"));
        }
        if (!auth.authenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false, "safeCode", "NETWORKCORE_INGRESS_AUTH_DENIED"));
        }
        if (body == null || !requestIdHeader.equals(body.requestId())) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "safeCode", "REQUEST_BINDING_MISMATCH"));
        }
        try {
            UUID uuid = UUID.fromString(body.playerUuid());
            String uuidText = uuid.toString();
            var message = ingress.minecraft(
                    new BuggyChannelPrincipal(new ActorId(uuidText), uuidText, uuidText),
                    "minecraft:" + uuidText,
                    body.server(),
                    body.requestId(),
                    body.message(),
                    Map.of("playerName", body.playerName()));
            return execute(message);
        } catch (IllegalArgumentException invalid) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "safeCode", "BUGGY_MESSAGE_INVALID"));
        }
    }

    @PostMapping("/messages/discord")
    public ResponseEntity<?> discordMessage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader("X-Request-Id") String requestIdHeader,
            @RequestBody DiscordMessage body) {
        RequestId requestId;
        try { requestId = new RequestId(requestIdHeader); }
        catch (RuntimeException invalid) { return ResponseEntity.badRequest().body(Map.of("ok", false, "safeCode", "REQUEST_ID_INVALID")); }
        ServiceAuthenticationResult auth = authentication.authenticate(authorization, requestId, clock.instant());
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("ok", false, "safeCode", "NETWORKCORE_INGRESS_SECURITY_UNAVAILABLE"));
        }
        if (!auth.authenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false, "safeCode", "NETWORKCORE_INGRESS_AUTH_DENIED"));
        }
        if (body == null || !requestIdHeader.equals(body.requestId())) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "safeCode", "REQUEST_BINDING_MISMATCH"));
        }
        try {
            UUID uuid = UUID.fromString(body.playerUuid());
            requireSnowflake(body.discordUserId(), "discordUserId");
            requireSnowflake(body.guildId(), "guildId");
            requireSnowflake(body.channelId(), "channelId");
            requireSnowflake(body.discordMessageId(), "discordMessageId");
            String uuidText = uuid.toString();
            String conversationKey = "discord:" + body.guildId() + ":" + body.channelId() + ":" + body.discordUserId();
            var message = ingress.discord(
                    new BuggyChannelPrincipal(new ActorId(uuidText), uuidText, body.discordUserId()),
                    conversationKey,
                    body.guildId(),
                    body.channelId(),
                    body.requestId(),
                    body.message(),
                    Map.of(
                            "discordMessageId", body.discordMessageId(),
                            "discordUserName", boundedMetadata(body.discordUserName())));
            return execute(message);
        } catch (IllegalArgumentException invalid) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "safeCode", "BUGGY_MESSAGE_INVALID"));
        }
    }

    private ResponseEntity<?> execute(de.legitserver.assistant.buggy.channel.BuggyInboundMessage message) {
        try {
            BuggyChannelExecutionPort port = execution.getIfAvailable();
            if (port == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("ok", false, "safeCode", "BUGGY_RUNTIME_NOT_CONFIGURED"));
            }
            BuggyChannelReply reply = port.execute(message);
            if (reply == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("ok", false, "safeCode", "BUGGY_RUNTIME_UNAVAILABLE"));
            }
            return ResponseEntity.ok(Map.of("ok", true, "message", reply.content(), "safeCode", reply.safeCode()));
        } catch (BuggyChannelExecutionException failure) {
            HttpStatus status = failure.kind() == BuggyChannelExecutionException.Kind.ACCESS_DENIED
                    ? HttpStatus.FORBIDDEN : HttpStatus.SERVICE_UNAVAILABLE;
            return ResponseEntity.status(status).body(Map.of("ok", false, "safeCode", failure.safeCode()));
        } catch (RuntimeException unavailable) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("ok", false, "safeCode", "BUGGY_RUNTIME_UNAVAILABLE"));
        }
    }

    private static void requireSnowflake(String value, String field) {
        if (value == null || !value.matches("[0-9]{15,22}")) throw new IllegalArgumentException(field + " invalid");
    }

    private static String boundedMetadata(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String normalized = value.strip();
        return normalized.length() <= 256 ? normalized : normalized.substring(0, 256);
    }

    public record MinecraftMessage(String requestId, String playerUuid, String playerName, String server, String message) { }
    public record DiscordMessage(String requestId, String playerUuid, String discordUserId, String discordUserName,
                                 String guildId, String channelId, String discordMessageId, String message) { }
}

