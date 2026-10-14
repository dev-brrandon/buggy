package de.legitserver.assistant.bootstrap.g12;

import de.legitserver.assistant.core.actor.ActorContext;
import de.legitserver.assistant.core.actor.ActorId;
import de.legitserver.assistant.core.actor.ActorType;
import de.legitserver.assistant.core.actor.Origin;
import de.legitserver.assistant.core.request.RequestId;
import de.legitserver.assistant.core.request.TraceId;
import de.legitserver.assistant.integrations.networkcore.NetworkCoreAuthorityClient;
import de.legitserver.assistant.observability.health.DependencyHealthRegistry;
import de.legitserver.assistant.observability.health.DependencyId;
import de.legitserver.assistant.observability.health.DependencyState;
import de.legitserver.assistant.security.authorization.AuthorityLookupStatus;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

/** Periodic first-party dependency probes used by public and management readiness surfaces. */
public final class G12DependencyHealthMonitor implements AutoCloseable {
    private static final UUID AUTHORITY_PROBE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final DataSource dataSource;
    private final NetworkCoreAuthorityClient authority;
    private final DependencyHealthRegistry health;
    private final Clock clock;
    private final ScheduledExecutorService executor;

    public G12DependencyHealthMonitor(
            DataSource dataSource,
            NetworkCoreAuthorityClient authority,
            DependencyHealthRegistry health,
            Clock clock) {
        if (dataSource == null || authority == null || health == null || clock == null) {
            throw new IllegalArgumentException("dependency health monitor dependencies required");
        }
        this.dataSource = dataSource;
        this.authority = authority;
        this.health = health;
        this.clock = clock;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "legit-assistant-g12-health");
            thread.setDaemon(true);
            return thread;
        });
    }

    public G12DependencyHealthMonitor start() {
        probeSafely();
        executor.scheduleWithFixedDelay(this::probeSafely, 10L, 10L, TimeUnit.SECONDS);
        return this;
    }

    private void probeSafely() {
        probeDatabase();
        probeNetworkCore();
    }

    private void probeDatabase() {
        long started = System.nanoTime();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(2);
            try (ResultSet result = statement.executeQuery("SELECT 1")) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new IllegalStateException("database probe returned unexpected result");
                }
            }
            health.update(DependencyId.DATABASE, DependencyState.UP, "UP", elapsedMillis(started));
        } catch (Exception unavailable) {
            health.update(DependencyId.DATABASE, DependencyState.DOWN, "DATABASE_UNAVAILABLE", elapsedMillis(started));
        }
    }

    private void probeNetworkCore() {
        long started = System.nanoTime();
        try {
            Instant now = clock.instant();
            String uuid = AUTHORITY_PROBE_UUID.toString();
            var lookup = authority.resolveActor(new ActorContext(
                    new ActorId(uuid),
                    ActorType.HUMAN,
                    Origin.INTERNAL,
                    uuid,
                    AUTHORITY_PROBE_UUID,
                    null,
                    null,
                    new RequestId("health-" + UUID.randomUUID()),
                    new TraceId("health-" + UUID.randomUUID()),
                    "assistant-health",
                    0L,
                    now));
            if (lookup.status() == AuthorityLookupStatus.UNAVAILABLE) {
                health.update(DependencyId.NETWORKCORE, DependencyState.DOWN, "NETWORKCORE_UNAVAILABLE", elapsedMillis(started));
                return;
            }
            health.update(DependencyId.NETWORKCORE, DependencyState.UP, "UP", elapsedMillis(started));
        } catch (RuntimeException unavailable) {
            health.update(DependencyId.NETWORKCORE, DependencyState.DOWN, "NETWORKCORE_UNAVAILABLE", elapsedMillis(started));
        }
    }

    private static long elapsedMillis(long started) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
