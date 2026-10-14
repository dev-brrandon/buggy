package de.legitserver.assistant.buggy.channel;

import de.legitserver.assistant.core.actor.Origin;

/** Trusted G1 ingress channels. Channel adapters assign these server-side; clients never grant authority. */
public enum BuggyChannelType {
    WEB(Origin.PANEL),
    MINECRAFT(Origin.MINECRAFT),
    DISCORD(Origin.DISCORD);

    private final Origin origin;

    BuggyChannelType(Origin origin) {
        this.origin = origin;
    }

    public Origin origin() {
        return origin;
    }
}
