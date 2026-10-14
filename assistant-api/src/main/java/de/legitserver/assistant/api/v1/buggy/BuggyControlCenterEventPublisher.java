package de.legitserver.assistant.api.v1.buggy;

import de.legitserver.assistant.panel.realtime.PanelRealtimeEvent;
import de.legitserver.assistant.panel.realtime.PanelRealtimeEventType;
import de.legitserver.assistant.panel.realtime.PanelRealtimeHub;
import java.util.Map;
import java.util.Objects;

/** Maps bounded Buggy product notifications onto the existing Foundation/Panel realtime hub. */
public final class BuggyControlCenterEventPublisher {
    private final PanelRealtimeHub hub;
    public BuggyControlCenterEventPublisher(PanelRealtimeHub hub) { this.hub = Objects.requireNonNull(hub, "hub"); }
    public PanelRealtimeEvent publish(PanelRealtimeEventType type, String resourceId, Map<String, String> safeAttributes) {
        if (!type.name().startsWith("BUGGY_")) throw new IllegalArgumentException("B8 publisher only accepts BUGGY_* events");
        return hub.publish(type, resourceId, safeAttributes);
    }
}
