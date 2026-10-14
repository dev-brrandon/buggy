package de.legitserver.assistant.integrations.networkcore;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.ActorType;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.security.authorization.AuthorityLookupStatus;
import de.legitserver.assistant.security.authorization.ResolvedActorIdentity;
import de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshot;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Verifies the transport-neutral NetworkCore authority adapter projection and fail-closed freshness semantics. */
public final class Phase3NetworkCoreAdapterSelfTest {
    private static final UUID ACTOR = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final Instant NOW = Instant.parse("2026-10-03T19:00:00Z");

    private Phase3NetworkCoreAdapterSelfTest() {}

    public static void main(String[] args) {
        AuthoritativeSecuritySnapshot snapshot = new AuthoritativeSecuritySnapshot(
                ACTOR,
                "developer",
                900,
                "DEVELOPMENT",
                Set.of("DEVELOPMENT"),
                Set.of("usr"),
                Set.of("networkcore.assistant.use.buggy", "networkcore.assistant.source.read"),
                Set.of("assistant.source.read"),
                Set.of("panel.technical"),
                true,
                42,
                NOW);
        require(snapshot.actorUuid().equals(ACTOR), "actor UUID must map");
        require(snapshot.securityRevision() == 42, "security revision must map");
        require(snapshot.hasPermission("networkcore.assistant.source.read"), "permissions must map");
        require(snapshot.hasCapability("assistant.source.read"), "capabilities must map");
        require(snapshot.controlOwner(), "control owner must map");

        ResolvedActorIdentity identity = new ResolvedActorIdentity(
                new ActorId("actor:" + ACTOR), "network:" + ACTOR, ACTOR, 42);
        NetworkCoreAuthorityClient client = new NetworkCoreAuthorityClient() {
            @Override
            public NetworkCoreActorLookup resolveActor(ActorContext presentedActor) {
                return new NetworkCoreActorLookup(AuthorityLookupStatus.FOUND, identity);
            }

            @Override
            public NetworkCoreSecurityLookup securitySnapshot(UUID actorUuid, long minimumRevision) {
                return new NetworkCoreSecurityLookup(AuthorityLookupStatus.FOUND, snapshot);
            }
        };
        NetworkCoreAuthorizationAdapter adapter = new NetworkCoreAuthorizationAdapter(client);
        ActorContext actor = new ActorContext(
                identity.actorId(),
                ActorType.HUMAN,
                Origin.PANEL,
                identity.networkIdentityId(),
                ACTOR,
                "panel-user",
                null,
                new RequestId("req-adapter"),
                new TraceId("trace-adapter"),
                null,
                42,
                NOW);
        require(adapter.resolve(actor).status() == AuthorityLookupStatus.FOUND, "actor adapter must resolve");
        require(adapter.lookup(ACTOR, 42).status() == AuthorityLookupStatus.FOUND, "snapshot adapter must resolve");
        require(adapter.lookup(ACTOR, 43).status() == AuthorityLookupStatus.UNAVAILABLE, "stale authority snapshot must not satisfy freshness requirement");

        NetworkCoreAuthorizationAdapter failing = new NetworkCoreAuthorizationAdapter(new NetworkCoreAuthorityClient() {
            @Override
            public NetworkCoreActorLookup resolveActor(ActorContext presentedActor) {
                throw new IllegalStateException("network down");
            }

            @Override
            public NetworkCoreSecurityLookup securitySnapshot(UUID actorUuid, long minimumRevision) {
                throw new IllegalStateException("network down");
            }
        });
        require(failing.resolve(actor).status() == AuthorityLookupStatus.UNAVAILABLE, "actor transport failure must fail closed");
        require(failing.lookup(ACTOR, 0).status() == AuthorityLookupStatus.UNAVAILABLE, "snapshot transport failure must fail closed");
        System.out.println("PHASE3_NETWORKCORE_ADAPTER_SELF_TEST: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
