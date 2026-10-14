package de.legitserver.assistant.panel.security;

import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.validation.CoreValidation;

/** Identity known by the trusted Panel Backend; no permissions or ranks are carried. */
public record PanelRequestIdentity(ActorId actorId, String networkIdentityId, String panelUserId, String serverId) {
    public PanelRequestIdentity {
        actorId = CoreValidation.requireNonNull(actorId, "actorId");
        networkIdentityId = CoreValidation.requireSafeToken(networkIdentityId, "networkIdentityId", 128);
        panelUserId = CoreValidation.requireSafeToken(panelUserId, "panelUserId", 128);
        serverId = CoreValidation.optionalSafeToken(serverId, "serverId", 128);
    }
}
