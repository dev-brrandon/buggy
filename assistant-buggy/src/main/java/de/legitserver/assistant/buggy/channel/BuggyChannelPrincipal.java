package de.legitserver.assistant.buggy.channel;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;

/** Cross-channel identity reference only. No role, permission or capability may be transported here. */
public record BuggyChannelPrincipal(
        ActorId actorId,
        String networkIdentityId,
        String channelUserId) {

    public BuggyChannelPrincipal {
        actorId = CoreValidation.requireNonNull(actorId, "actorId");
        networkIdentityId = CoreValidation.requireSafeToken(networkIdentityId, "networkIdentityId", 128);
        channelUserId = CoreValidation.requireSafeToken(channelUserId, "channelUserId", 128);
    }
}
