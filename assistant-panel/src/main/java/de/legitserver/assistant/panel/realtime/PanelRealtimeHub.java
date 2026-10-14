package de.legitserver.assistant.panel.realtime;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Bounded in-process Phase-15 SSE fan-out. It carries only sanitized notification metadata. */
public final class PanelRealtimeHub {
    private final int historyCapacity;
    private final int maxSubscribers;
    private final Clock clock;
    private final AtomicLong sequence = new AtomicLong();
    private final ArrayDeque<PanelRealtimeEvent> history = new ArrayDeque<>();
    private final CopyOnWriteArrayList<Consumer<PanelRealtimeEvent>> subscribers = new CopyOnWriteArrayList<>();

    public PanelRealtimeHub(int historyCapacity, int maxSubscribers, Clock clock) {
        if (historyCapacity < 16 || historyCapacity > 10_000 || maxSubscribers < 1 || maxSubscribers > 2_000 || clock == null) {
            throw new IllegalArgumentException("invalid realtime hub bounds");
        }
        this.historyCapacity = historyCapacity;
        this.maxSubscribers = maxSubscribers;
        this.clock = clock;
    }

    public PanelRealtimeEvent publish(PanelRealtimeEventType type, String resourceId, Map<String, String> attributes) {
        PanelRealtimeEvent event = new PanelRealtimeEvent(
                sequence.incrementAndGet(), type, resourceId, attributes, clock.instant());
        synchronized (history) {
            history.addLast(event);
            while (history.size() > historyCapacity) history.removeFirst();
        }
        for (Consumer<PanelRealtimeEvent> subscriber : subscribers) subscriber.accept(event);
        return event;
    }

    public AutoCloseable subscribe(Consumer<PanelRealtimeEvent> subscriber) {
        if (subscriber == null) throw new IllegalArgumentException("subscriber must not be null");
        if (subscribers.size() >= maxSubscribers) throw new IllegalStateException("realtime subscriber limit reached");
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    public List<PanelRealtimeEvent> replayAfter(long afterSequence, int limit) {
        if (afterSequence < 0 || limit < 1 || limit > 500) throw new IllegalArgumentException("invalid realtime replay bounds");
        synchronized (history) {
            List<PanelRealtimeEvent> result = new ArrayList<>();
            for (PanelRealtimeEvent event : history) {
                if (event.sequence() > afterSequence) {
                    result.add(event);
                    if (result.size() >= limit) break;
                }
            }
            return List.copyOf(result);
        }
    }

    public int subscriberCount() { return subscribers.size(); }
    public long latestSequence() { return sequence.get(); }
}
