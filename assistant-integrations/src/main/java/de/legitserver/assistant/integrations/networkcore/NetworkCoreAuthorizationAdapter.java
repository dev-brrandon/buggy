package de.legitserver.assistant.integrations.networkcore;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.observability.metrics.AssistantMetric;
import de.legitserver.assistant.observability.metrics.AssistantMetrics;
import de.legitserver.assistant.observability.metrics.NoOpAssistantMetrics;
import de.legitserver.assistant.security.authorization.ActorResolution;
import de.legitserver.assistant.security.authorization.AuthoritativeActorResolverPort;
import de.legitserver.assistant.security.authorization.AuthoritativeSecuritySnapshotPort;
import de.legitserver.assistant.security.authorization.AuthorityLookupStatus;
import de.legitserver.assistant.security.authorization.SecuritySnapshotLookup;
import java.time.Duration;
import java.util.UUID;

/** Fail-closed NetworkCore authority adapter. Transport failures become UNAVAILABLE, never implicit allow. */
public final class NetworkCoreAuthorizationAdapter
        implements AuthoritativeActorResolverPort, AuthoritativeSecuritySnapshotPort {
    private final NetworkCoreAuthorityClient client;
    private final AssistantMetrics metrics;

    public NetworkCoreAuthorizationAdapter(NetworkCoreAuthorityClient client) {
        this(client, NoOpAssistantMetrics.INSTANCE);
    }

    public NetworkCoreAuthorizationAdapter(NetworkCoreAuthorityClient client, AssistantMetrics metrics) {
        if (client == null || metrics == null) throw new IllegalArgumentException("client and metrics must not be null");
        this.client = client;
        this.metrics = metrics;
    }

    @Override
    public ActorResolution resolve(ActorContext presentedActor) {
        if (presentedActor == null) return ActorResolution.notFound();
        long started = System.nanoTime();
        try {
            NetworkCoreActorLookup lookup = client.resolveActor(presentedActor);
            if (lookup == null || lookup.status() == AuthorityLookupStatus.UNAVAILABLE) return ActorResolution.unavailable();
            if (lookup.status() == AuthorityLookupStatus.NOT_FOUND) return ActorResolution.notFound();
            return ActorResolution.found(lookup.identity());
        } catch (RuntimeException unavailable) {
            return ActorResolution.unavailable();
        } finally {
            metrics.recordLatency(
                    AssistantMetric.NETWORKCORE_LATENCY,
                    Duration.ofNanos(Math.max(0L, System.nanoTime() - started)));
        }
    }

    @Override
    public SecuritySnapshotLookup lookup(UUID actorUuid, long minimumRevision) {
        if (actorUuid == null || minimumRevision < 0) return SecuritySnapshotLookup.unavailable();
        long started = System.nanoTime();
        try {
            NetworkCoreSecurityLookup lookup = client.securitySnapshot(actorUuid, minimumRevision);
            if (lookup == null || lookup.status() == AuthorityLookupStatus.UNAVAILABLE) return SecuritySnapshotLookup.unavailable();
            if (lookup.status() == AuthorityLookupStatus.NOT_FOUND) return SecuritySnapshotLookup.notFound();
            var snapshot = lookup.snapshot();
            if (snapshot.securityRevision() < minimumRevision) {
                return SecuritySnapshotLookup.unavailable();
            }
            return SecuritySnapshotLookup.found(snapshot);
        } catch (RuntimeException unavailable) {
            return SecuritySnapshotLookup.unavailable();
        } finally {
            metrics.recordLatency(
                    AssistantMetric.NETWORKCORE_LATENCY,
                    Duration.ofNanos(Math.max(0L, System.nanoTime() - started)));
        }
    }
}
