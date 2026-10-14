package de.legitserver.assistant.buggy.controlcenter;

import de.legitserver.assistant.buggy.activity.BuggyActivityEntry;
import de.legitserver.assistant.buggy.automation.BuggyAutomationRevision;
import de.legitserver.assistant.buggy.identity.BuggyIdentitySnapshot;
import de.legitserver.assistant.buggy.personality.BuggyPersonalityRevision;
import de.legitserver.assistant.buggy.personality.BuggyPersonalitySnapshot;
import de.legitserver.assistant.buggy.repository.BuggyActivityRepository;
import de.legitserver.assistant.buggy.repository.BuggyAutomationRepository;
import de.legitserver.assistant.buggy.repository.BuggyPersonalityRepository;
import de.legitserver.assistant.buggy.repository.BuggySelfDevelopmentRepository;
import de.legitserver.assistant.buggy.repository.BuggyTaskRepository;
import de.legitserver.assistant.buggy.selfdev.BuggySelfDevelopmentRevision;
import de.legitserver.assistant.buggy.service.BuggyIdentityService;
import de.legitserver.assistant.buggy.tasks.BuggyTaskRevision;
import de.legitserver.assistant.buggy.tasks.BuggyTaskStatus;
import de.legitserver.assistant.buggy.tools.BuggySelfDevelopmentToolCatalog;
import de.legitserver.assistant.buggy.tools.BuggyToolCatalog;
import de.legitserver.assistant.buggy.tools.BuggyToolDefinition;
import de.legitserver.assistant.runtime.registry.ToolRegistry;
import de.legitserver.assistant.security.control.SecurityControlPlane;
import de.legitserver.assistant.security.control.SecurityControlState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * B8 read-only projection over canonical B1-B7 state. It owns no authority and no write store.
 * Foundation approvals/incidents/knowledge/audit are intentionally projected by the API integration layer.
 */
public final class BuggyControlCenterReadService {
    private final BuggyIdentityService identities;
    private final BuggyPersonalityRepository personalities;
    private final BuggyTaskRepository tasks;
    private final BuggyActivityRepository activity;
    private final BuggyAutomationRepository automations;
    private final BuggySelfDevelopmentRepository selfDevelopment;
    private final ToolRegistry toolRegistry;
    private final SecurityControlPlane securityControls;

    public BuggyControlCenterReadService(
            BuggyIdentityService identities,
            BuggyPersonalityRepository personalities,
            BuggyTaskRepository tasks,
            BuggyActivityRepository activity,
            BuggyAutomationRepository automations,
            BuggySelfDevelopmentRepository selfDevelopment,
            ToolRegistry toolRegistry,
            SecurityControlPlane securityControls) {
        this.identities = Objects.requireNonNull(identities, "identities");
        this.personalities = Objects.requireNonNull(personalities, "personalities");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.activity = Objects.requireNonNull(activity, "activity");
        this.automations = Objects.requireNonNull(automations, "automations");
        this.selfDevelopment = Objects.requireNonNull(selfDevelopment, "selfDevelopment");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.securityControls = Objects.requireNonNull(securityControls, "securityControls");
    }

    public BuggyControlCenterResource overview() {
        BuggyIdentitySnapshot identity = identities.loadRequired();
        BuggyPersonalitySnapshot personality = personalities.find().orElse(null);
        List<BuggyTaskRevision> currentTasks = tasks.listCurrentTasks(100);
        List<BuggyActivityEntry> recent = activity.recent(10);
        List<BuggyAutomationRevision> currentAutomations = automations.listCurrentAutomations(100);
        List<BuggySelfDevelopmentRevision> currentSelfDev = selfDevelopment.listCurrentItems(100);
        SecurityControlState controls = securityControls.current();
        long waiting = currentTasks.stream().filter(t -> t.status() == BuggyTaskStatus.WAITING_APPROVAL
                || t.status() == BuggyTaskStatus.WAITING_DEPENDENCY).count();
        long active = currentTasks.stream().filter(t -> t.status() == BuggyTaskStatus.ACTIVE).count();
        long enabledAutomations = currentAutomations.stream().filter(a -> a.state().name().equals("ENABLED")).count();
        long selfDevWaiting = currentSelfDev.stream().filter(s -> s.state().name().equals("WAITING_FOR_OWNER_APPROVAL")).count();
        long failedTasks = currentTasks.stream().filter(t -> t.status() == BuggyTaskStatus.FAILED).count();
        long autonomousActions = recent.stream().filter(a -> a.type().name().equals("AUTOMATION_ACTED")).count();
        long automationFailures = recent.stream().filter(a -> a.type().name().equals("AUTOMATION_FAILED")).count();
        String currentActivity = recent.isEmpty() ? "IDLE" : recent.get(0).summary();
        return resource("buggy-overview", Map.ofEntries(
                Map.entry("healthy", true),
                Map.entry("currentActivity", currentActivity),
                Map.entry("assistantId", identity.identityCore().assistantId().name()),
                Map.entry("displayName", identity.identityCore().displayName()),
                Map.entry("identityRevision", identity.headRevision()),
                Map.entry("configurationRevision", identity.configuration().revision()),
                Map.entry("personalityRevision", personality == null ? 0L : personality.current().revision()),
                Map.entry("operationalMode", controls.operationalMode().name()),
                Map.entry("securityRevision", controls.revision()),
                Map.entry("activeTasks", active),
                Map.entry("waitingTasks", waiting),
                Map.entry("enabledAutomations", enabledAutomations),
                Map.entry("selfDevelopmentWaitingApproval", selfDevWaiting),
                Map.entry("pendingOwnerDecisions", waiting + selfDevWaiting),
                Map.entry("failedTasks", failedTasks),
                Map.entry("recentAutonomousActions", autonomousActions),
                Map.entry("recentAutomationFailures", automationFailures),
                Map.entry("recentActivityCount", recent.size()),
                Map.entry("foundationSecurityAuthority", identity.identityCore().securityAuthority()),
                Map.entry("capabilityProfileId", identity.configuration().manifest().capabilityProfileId()),
                Map.entry("runtimeProfileId", identity.configuration().manifest().runtimeProfileId())));
    }

    public BuggyControlCenterResource identity() {
        BuggyIdentitySnapshot s = identities.loadRequired();
        return resource("buggy", Map.ofEntries(
                Map.entry("assistantId", s.identityCore().assistantId().name()),
                Map.entry("canonicalId", s.identityCore().canonicalId()),
                Map.entry("displayName", s.identityCore().displayName()),
                Map.entry("serviceIdentity", s.identityCore().serviceIdentity()),
                Map.entry("assistantType", s.identityCore().assistantType()),
                Map.entry("primaryRole", s.identityCore().primaryRole()),
                Map.entry("authorityTier", s.identityCore().authorityTier()),
                Map.entry("securityAuthority", s.identityCore().securityAuthority()),
                Map.entry("scopes", s.identityCore().scopes().stream().sorted().toList()),
                Map.entry("principles", s.identityCore().principles()),
                Map.entry("coreVersion", s.identityCore().coreVersion()),
                Map.entry("coreSha256", s.identityCore().sha256()),
                Map.entry("ownerActorId", s.ownerBinding().ownerActorId().value()),
                Map.entry("ownerAuthoritySource", s.ownerBinding().authoritySource()),
                Map.entry("configurationRevision", s.configuration().revision()),
                Map.entry("configurationSha256", s.configuration().sha256()),
                Map.entry("protectedIdentityCore", true)));
    }

    public BuggyControlCenterResource personality() {
        BuggyPersonalitySnapshot snapshot = personalities.find().orElseThrow(() -> new IllegalStateException("Buggy personality unavailable"));
        BuggyPersonalityRevision current = snapshot.current();
        return resource("buggy-personality", Map.ofEntries(
                Map.entry("seedId", snapshot.seed().profileId()),
                Map.entry("seedVersion", snapshot.seed().seedVersion()),
                Map.entry("currentRevision", current.revision()),
                Map.entry("revisionId", current.revisionId()),
                Map.entry("origin", current.origin().name()),
                Map.entry("reason", current.reason()),
                Map.entry("confidenceBps", current.confidenceBps()),
                Map.entry("changedAttributes", current.changedAttributes()),
                Map.entry("entryCount", current.entries().size()),
                Map.entry("historyCount", personalities.history().size()),
                Map.entry("observationCount", personalities.observations().size()),
                Map.entry("updatedAt", snapshot.updatedAt().toString())));
    }

    public List<BuggyControlCenterResource> tasks(int limit) {
        return tasks.listCurrentTasks(limit).stream().map(BuggyControlCenterReadService::task).toList();
    }

    public List<BuggyControlCenterResource> activity(int limit) {
        return activity.recent(limit).stream().map(BuggyControlCenterReadService::activity).toList();
    }

    public List<BuggyControlCenterResource> automations(int limit) {
        return automations.listCurrentAutomations(limit).stream().map(this::automation).toList();
    }

    public List<BuggyControlCenterResource> selfDevelopment(int limit) {
        return selfDevelopment.listCurrentItems(limit).stream().map(this::selfDevelopment).toList();
    }

    public List<BuggyControlCenterResource> tools() {
        ArrayList<BuggyControlCenterResource> out = new ArrayList<>();
        for (BuggyToolDefinition definition : BuggyToolCatalog.definitions()) {
            var d = definition.descriptor();
            out.add(resource(d.toolId().value(), Map.ofEntries(
                    Map.entry("catalogId", BuggyToolCatalog.CATALOG_ID),
                    Map.entry("category", definition.category().name()),
                    Map.entry("risk", d.riskLevel().name()),
                    Map.entry("status", toolRegistry.find(d.toolId()).isPresent() ? "REGISTERED" : "DEFINED"),
                    Map.entry("allowedResources", definition.allowedResourceTypes().stream().sorted().toList()),
                    Map.entry("capability", d.capability().value()),
                    Map.entry("approvalRule", definition.approvalPolicy().name()),
                    Map.entry("selfHealingEligible", d.selfHealingEligible()),
                    Map.entry("auditPolicy", d.auditPolicy().name()),
                    Map.entry("timeout", d.timeout().toString()),
                    Map.entry("maxConcurrency", d.maxConcurrency()))));
        }
        BuggySelfDevelopmentToolCatalog.definitions().forEach(definition -> {
            var d = definition.descriptor();
            out.add(resource(d.toolId().value(), Map.ofEntries(
                    Map.entry("catalogId", BuggySelfDevelopmentToolCatalog.CATALOG_ID),
                    Map.entry("category", "SELF_DEVELOPMENT"),
                    Map.entry("risk", d.riskLevel().name()),
                    Map.entry("status", toolRegistry.find(d.toolId()).isPresent() ? "REGISTERED" : "DEFINED"),
                    Map.entry("allowedResources", definition.allowedResourceTypes().stream().sorted().toList()),
                    Map.entry("capability", d.capability().value()),
                    Map.entry("approvalRule", definition.approvalPolicy().name()),
                    Map.entry("selfHealingEligible", d.selfHealingEligible()),
                    Map.entry("auditPolicy", d.auditPolicy().name()),
                    Map.entry("timeout", d.timeout().toString()),
                    Map.entry("maxConcurrency", d.maxConcurrency()))));
        });
        out.sort(Comparator.comparing(BuggyControlCenterResource::id));
        return List.copyOf(out);
    }

    public BuggyControlCenterResource security() {
        BuggyIdentitySnapshot identity = identities.loadRequired();
        SecurityControlState state = securityControls.current();
        return resource("buggy-security", Map.ofEntries(
                Map.entry("authority", "NetworkCore"),
                Map.entry("boundaryStatement", "NetworkCore authoritative. Buggy cannot autonomously modify this boundary."),
                Map.entry("capabilityProfileId", identity.configuration().manifest().capabilityProfileId()),
                Map.entry("operationalMode", state.operationalMode().name()),
                Map.entry("enabledFeatures", state.enabledFeatures().stream().map(Enum::name).sorted().toList()),
                Map.entry("activeKillSwitches", state.activeKillSwitches().stream().map(Enum::name).sorted().toList()),
                Map.entry("controlRevision", state.revision()),
                Map.entry("changedAt", state.changedAt().toString()),
                Map.entry("selfApprovalAllowed", false),
                Map.entry("authoritySelfEscalationAllowed", false),
                Map.entry("riskDowngradeAllowed", false)));
    }

    private BuggyControlCenterResource automation(BuggyAutomationRevision a) {
        var lastRun = automations.latestRun(a.automationId());
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("revision", a.revision()); m.put("name", a.name()); m.put("state", a.state().name());
        m.put("trigger", a.triggerType().name()); m.put("signal", a.signalType() == null ? null : a.signalType().name());
        m.put("scheduleInterval", a.scheduleInterval() == null ? null : a.scheduleInterval().toString());
        m.put("maxLevel", a.maxLevel().name()); m.put("attention", a.attention().name());
        m.put("actionToolId", a.actionToolId().value()); m.put("capability", a.capabilityId().value());
        m.put("risk", a.riskLevel().name()); m.put("maxAttempts", a.maxAttemptsPerTarget());
        m.put("cooldown", a.cooldown().toString()); m.put("window", a.window().toString());
        m.put("maxActionsPerWindow", a.maxActionsPerWindow()); m.put("sameTargetSuppression", a.sameTargetSuppression().toString());
        m.put("activationRequestId", a.activationRequestId() == null ? null : a.activationRequestId().toString());
        m.put("changedAt", a.changedAt().toString());
        lastRun.ifPresent(run -> { m.put("lastRunId", run.runId().toString()); m.put("lastRunSuccessful", run.successful()); m.put("lastRunSafeCode", run.safeCode()); m.put("lastRunCompletedAt", run.completedAt().toString()); });
        return resource(a.automationId(), m);
    }

    private BuggyControlCenterResource selfDevelopment(BuggySelfDevelopmentRevision s) {
        var evidence = selfDevelopment.evidence(s.itemId());
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("revision", s.revision()); m.put("state", s.state().name()); m.put("title", s.title());
        m.put("problem", s.problem()); m.put("proposalSummary", s.proposalSummary());
        m.put("changeType", s.changeType() == null ? null : s.changeType().name());
        m.put("sourceComponent", s.sourceSnapshot().component()); m.put("sourceVersion", s.sourceSnapshot().version());
        m.put("sourceRevision", s.sourceSnapshot().revision()); m.put("workspaceRef", s.workspaceRef());
        m.put("patchRef", s.patchRef()); m.put("buildArtifactRef", s.buildArtifactRef());
        m.put("risk", s.riskLevel() == null ? null : s.riskLevel().name());
        m.put("approvalRequestId", s.approvalBinding() == null ? null : s.approvalBinding().requestId().toString());
        m.put("reason", s.reason()); m.put("evidenceCount", evidence.size());
        m.put("successfulEvidenceCount", evidence.stream().filter(e -> e.successful()).count());
        m.put("changedAt", s.changedAt().toString());
        return resource(s.itemId().toString(), m);
    }

    private static BuggyControlCenterResource task(BuggyTaskRevision t) {
        return resource(t.taskId().toString(), Map.ofEntries(
                Map.entry("revision", t.revision()), Map.entry("title", t.title()), Map.entry("description", t.description()),
                Map.entry("status", t.status().name()), Map.entry("attention", t.attention().name()),
                Map.entry("progressPercent", t.progressPercent()), Map.entry("waitingReason", t.waitingReason() == null ? "" : t.waitingReason()),
                Map.entry("changedBy", t.changedBy().value()), Map.entry("changedAt", t.changedAt().toString())));
    }

    private static BuggyControlCenterResource activity(BuggyActivityEntry a) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("type", a.type().name()); m.put("attention", a.attention().name());
        m.put("taskId", a.taskId() == null ? null : a.taskId().toString()); m.put("automationId", a.automationId());
        m.put("summary", a.summary()); m.put("details", a.details()); m.put("actorId", a.actorId().value());
        m.put("occurredAt", a.occurredAt().toString());
        return resource(a.activityId().toString(), m);
    }

    private static BuggyControlCenterResource resource(String id, Map<String, Object> attributes) {
        return new BuggyControlCenterResource(id, attributes);
    }
}
