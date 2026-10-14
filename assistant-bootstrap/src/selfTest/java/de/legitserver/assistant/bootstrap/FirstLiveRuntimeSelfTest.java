package de.legitserver.assistant.bootstrap;

import de.legitserver.assistant.bootstrap.g12.G12DependencyHealthMonitor;
import de.legitserver.assistant.integrations.networkcore.NetworkCoreActorLookup;
import de.legitserver.assistant.integrations.networkcore.NetworkCoreAuthorityClient;
import de.legitserver.assistant.integrations.networkcore.NetworkCoreSecurityLookup;
import de.legitserver.assistant.observability.health.DependencyHealthRegistry;
import de.legitserver.assistant.observability.health.DependencyId;
import de.legitserver.assistant.observability.health.DependencyState;
import de.legitserver.assistant.security.authorization.AuthorityLookupStatus;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Offline verification for the first-live database and NetworkCore readiness monitor. */
public final class FirstLiveRuntimeSelfTest {
    private FirstLiveRuntimeSelfTest() {}

    public static void main(String[] args) {
        DependencyHealthRegistry health = new DependencyHealthRegistry();
        NetworkCoreAuthorityClient authority = new NetworkCoreAuthorityClient() {
            @Override
            public NetworkCoreActorLookup resolveActor(de.legitserver.assistant.core.actor.ActorContext presentedActor) {
                return new NetworkCoreActorLookup(AuthorityLookupStatus.NOT_FOUND, null);
            }

            @Override
            public NetworkCoreSecurityLookup securitySnapshot(java.util.UUID actorUuid, long minimumRevision) {
                return new NetworkCoreSecurityLookup(AuthorityLookupStatus.NOT_FOUND, null);
            }
        };
        G12DependencyHealthMonitor healthyMonitor = new G12DependencyHealthMonitor(
                healthyDataSource(), authority, health, Clock.systemUTC()).start();
        try {
            require(health.get(DependencyId.DATABASE).state() == DependencyState.UP, "database probe must be UP");
            require(health.get(DependencyId.NETWORKCORE).state() == DependencyState.UP,
                    "authenticated NOT_FOUND authority response still proves NetworkCore transport is UP");
        } finally {
            healthyMonitor.close();
        }

        DependencyHealthRegistry failed = new DependencyHealthRegistry();
        NetworkCoreAuthorityClient unavailable = new NetworkCoreAuthorityClient() {
            @Override
            public NetworkCoreActorLookup resolveActor(de.legitserver.assistant.core.actor.ActorContext presentedActor) {
                return new NetworkCoreActorLookup(AuthorityLookupStatus.UNAVAILABLE, null);
            }

            @Override
            public NetworkCoreSecurityLookup securitySnapshot(java.util.UUID actorUuid, long minimumRevision) {
                return new NetworkCoreSecurityLookup(AuthorityLookupStatus.UNAVAILABLE, null);
            }
        };
        G12DependencyHealthMonitor failedMonitor = new G12DependencyHealthMonitor(
                failingDataSource(), unavailable, failed, Clock.systemUTC()).start();
        try {
            require(failed.get(DependencyId.DATABASE).state() == DependencyState.DOWN, "database failure must be DOWN");
            require(failed.get(DependencyId.NETWORKCORE).state() == DependencyState.DOWN, "authority failure must be DOWN");
        } finally {
            failedMonitor.close();
        }
        System.out.println("FIRST_LIVE_RUNTIME_SELF_TEST: PASS");
    }

    private static DataSource healthyDataSource() {
        ResultSet result = proxy(ResultSet.class, (method, args) -> switch (method.getName()) {
            case "next" -> true;
            case "getInt" -> 1;
            default -> defaultValue(method.getReturnType());
        });
        Statement statement = proxy(Statement.class, (method, args) -> switch (method.getName()) {
            case "executeQuery" -> result;
            default -> defaultValue(method.getReturnType());
        });
        Connection connection = proxy(Connection.class, (method, args) -> switch (method.getName()) {
            case "createStatement" -> statement;
            default -> defaultValue(method.getReturnType());
        });
        return dataSource(connection, null);
    }

    private static DataSource failingDataSource() {
        return dataSource(null, new java.sql.SQLException("offline"));
    }

    private static DataSource dataSource(Connection connection, java.sql.SQLException failure) {
        return new DataSource() {
            @Override public Connection getConnection() throws java.sql.SQLException {
                if (failure != null) throw failure;
                return connection;
            }
            @Override public Connection getConnection(String username, String password) throws java.sql.SQLException { return getConnection(); }
            @Override public PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(PrintWriter out) { }
            @Override public void setLoginTimeout(int seconds) { }
            @Override public int getLoginTimeout() { return 0; }
            @Override public Logger getParentLogger() { return Logger.getGlobal(); }
            @Override public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
            @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("close")) return null;
            if (method.getName().equals("isClosed")) return false;
            if (method.getDeclaringClass() == Object.class) return method.invoke(proxy, args);
            return handler.invoke(method, args);
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    @FunctionalInterface
    private interface Handler {
        Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }
}
