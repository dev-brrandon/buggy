package de.legitserver.assistant.buggy;

import de.legitserver.assistant.buggy.channel.BuggyChannelIngress;
import de.legitserver.assistant.buggy.channel.BuggyChannelPrincipal;
import de.legitserver.assistant.buggy.channel.BuggyChannelType;
import de.legitserver.assistant.core.actor.ActorId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

/** G1.0 verifies one authority-neutral envelope for Web, Minecraft and Discord. */
public final class G10BuggyChannelIngressSelfTest {
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    private G10BuggyChannelIngressSelfTest() {}

    public static void main(String[] args) {
        BuggyChannelIngress ingress = new BuggyChannelIngress(Clock.fixed(NOW, ZoneOffset.UTC));
        BuggyChannelPrincipal principal = new BuggyChannelPrincipal(
                new ActorId("11111111-1111-4111-8111-111111111111"),
                "11111111-1111-4111-8111-111111111111",
                "panel-user-1");

        var web = ingress.web(principal, "conv-web-1", "msg-web-1", "Hallo Buggy", Map.of("surface", "chat"));
        require(web.context().channel() == BuggyChannelType.WEB, "web channel");
        require(web.context().channel().origin().name().equals("PANEL"), "web origin");
        require(web.receivedAt().equals(NOW), "clock bound");

        var minecraft = ingress.minecraft(principal, "conv-mc-1", "survival", "msg-mc-1", "@BuggyBot status", Map.of());
        require(minecraft.context().channel() == BuggyChannelType.MINECRAFT, "minecraft channel");
        require("survival".equals(minecraft.context().serverId()), "server context");

        var discord = ingress.discord(principal, "conv-dc-1", "guild-1", "channel-1", "msg-dc-1", "@BuggyBot hi", Map.of());
        require(discord.context().channel() == BuggyChannelType.DISCORD, "discord channel");
        require("guild-1".equals(discord.context().guildId()), "guild context");

        expectIllegal(() -> new BuggyChannelPrincipal(new ActorId("actor"), "network", "bad user id"));
        expectIllegal(() -> ingress.web(principal, "conv", "msg", "   ", Map.of()));
        System.out.println("G1_0_BUGGY_CHANNEL_INGRESS_SELF_TEST: PASS");
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            throw new IllegalStateException("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("G1.0 failed: " + message);
    }
}
