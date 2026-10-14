package de.legitserver.assistant.integrations.networkcore;

import de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshot;
import de.legitserver.platform.api.security.ActorSecuritySnapshot;

/** Maps the canonical LegitPlatform security snapshot into the Assistant framework-free projection. */
public final class LegitPlatformSecuritySnapshotMapper {
    private LegitPlatformSecuritySnapshotMapper() {}

    public static AuthoritativeSecuritySnapshot map(ActorSecuritySnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot must not be null");
        return new AuthoritativeSecuritySnapshot(
                snapshot.actorUuid(),
                snapshot.rankId(),
                snapshot.rankWeight(),
                snapshot.primaryDomainId(),
                snapshot.domainIds(),
                snapshot.bundleIds(),
                snapshot.effectivePermissions(),
                snapshot.effectiveCapabilities(),
                snapshot.panelCapabilities(),
                snapshot.controlOwner(),
                snapshot.securityRevision(),
                snapshot.createdAt());
    }
}
