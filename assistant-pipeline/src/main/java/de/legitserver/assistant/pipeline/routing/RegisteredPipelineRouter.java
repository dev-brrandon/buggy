package de.legitserver.assistant.pipeline.routing;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.pipeline.model.PipelineExecutionContext;
import de.legitserver.assistant.pipeline.model.PipelineExecutionOutput;
import de.legitserver.assistant.pipeline.model.PipelineOperation;
import de.legitserver.assistant.pipeline.model.PipelineRoute;
import de.legitserver.assistant.pipeline.port.PipelineExecutionPort;
import de.legitserver.assistant.pipeline.port.PipelineRouterPort;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Explicit typed route registry. It intentionally has no generic command/shell route. */
public final class RegisteredPipelineRouter implements PipelineRouterPort, PipelineExecutionPort {
    private final ConcurrentHashMap<Key, Entry> entries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Entry> byRouteId = new ConcurrentHashMap<>();

    public void register(
            PipelineOperation operation,
            PipelineRoute route,
            PipelineExecutionPort handler) {
        if (operation == null || route == null || handler == null) {
            throw new IllegalArgumentException("route registration fields must not be null");
        }
        Entry entry = new Entry(route, handler);
        Key key = new Key(operation, route.capabilityId());
        if (entries.putIfAbsent(key, entry) != null || byRouteId.putIfAbsent(route.routeId(), entry) != null) {
            entries.remove(key, entry);
            byRouteId.remove(route.routeId(), entry);
            throw new IllegalStateException("duplicate pipeline route");
        }
    }

    @Override
    public Optional<PipelineRoute> route(PipelineOperation operation, AssistantCapabilityId capabilityId) {
        Entry entry = entries.get(new Key(operation, capabilityId));
        return entry == null ? Optional.empty() : Optional.of(entry.route);
    }

    @Override
    public PipelineExecutionOutput execute(PipelineExecutionContext context) throws Exception {
        Entry entry = byRouteId.get(context.route().routeId());
        if (entry == null || !entry.route.equals(context.route())) throw new IllegalStateException("route not registered");
        return entry.handler.execute(context);
    }

    public Map<String, PipelineRoute> snapshot() {
        return byRouteId.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> entry.getValue().route));
    }

    private record Key(PipelineOperation operation, AssistantCapabilityId capabilityId) {}
    private record Entry(PipelineRoute route, PipelineExecutionPort handler) {}
}
