const PLANES = Object.freeze({
  COMMAND: Object.freeze([
    surface("overview", "Overview", "Live command summary", "read"),
    surface("chat", "Chat", "Context-aware Owner channel", "host"),
    surface("tasks", "Tasks", "Durable work and waiting states", "read"),
    surface("approvals", "Approvals", "Owner decisions with evidence", "read"),
    surface("bugs", "Bugs", "Canonical Bug workspace", "host")
  ]),
  OBSERVABILITY: Object.freeze([
    surface("activity", "Activity", "Owner-readable work history", "read"),
    surface("incidents", "Incidents", "Correlated operational incidents", "read"),
    surface("infrastructure", "Infrastructure", "Host and service health", "host"),
    surface("deployments", "Deployments", "Rollout and rollback state", "host"),
    surface("security", "Security", "Authority boundary and controls", "read"),
    surface("audit", "Audit", "Immutable security/compliance trail", "read")
  ]),
  EVOLUTION: Object.freeze([
    surface("knowledge", "Knowledge", "Learned items, conflicts and decisions", "read"),
    surface("self-development", "Self-Development", "Experiments and promotion evidence", "read"),
    surface("automations", "Automations", "Triggers, budgets and runtime state", "read"),
    surface("tools", "Tools", "Typed tool matrix", "read"),
    surface("assistants", "Assistants", "Supervisor-facing assistant health", "host"),
    surface("personality", "Memory / Personality", "Identity, seed and evolution history", "read"),
    surface("settings", "Settings", "Panel-owned Buggy presentation settings", "host")
  ])
});

const ALL_SURFACES = new Map(Object.entries(PLANES).flatMap(([plane, items]) => items.map(item => [item.id, Object.freeze({...item, plane})])));
const SEARCHABLE = new Set(["knowledge", "audit"]);
const CONTEXT_SURFACES = new Set(["tasks", "approvals", "incidents", "knowledge", "self-development", "activity"]);
const EMERGENCY_LABELS = Object.freeze({
  "buggy.emergency.pause": "Pause Buggy",
  "buggy.emergency.read-only": "Read only",
  "buggy.emergency.disable-automations": "Disable automations",
  "buggy.emergency.disable-self-healing": "Disable self-healing",
  "buggy.emergency.disable-tools": "Disable tools",
  "buggy.emergency.maintenance": "Maintenance mode"
});

const state = {
  bridge: null,
  currentSurface: "overview",
  context: readInitialContext(),
  search: "",
  subscription: null,
  refreshTimer: null,
  connected: false
};

const nodes = {};
document.addEventListener("DOMContentLoaded", bootstrap, {once: true});

function surface(id, label, subtitle, source) {
  return Object.freeze({id, label, subtitle, source});
}

function bootstrap() {
  bindNodes();
  renderNavigation();
  bindStaticEvents();
  renderContext();

  const candidate = window.LegitPanelAssistantBridge?.buggy;
  if (!isValidBridge(candidate)) {
    setConnection(false, "Panel Backend bridge unavailable", "Fail-closed: direct Assistant API access is intentionally disabled.");
    disableEmergencyControls(true);
    showAlert("error", "Buggy Control Center requires the trusted Panel Backend bridge. No direct API fallback is permitted.");
    renderDisconnected();
    return;
  }

  state.bridge = candidate;
  state.connected = true;
  setConnection(true, "Panel Backend connected", "Server-side authorization remains authoritative.");
  disableEmergencyControls(typeof state.bridge.command !== "function");
  attachRealtime();
  selectSurface("overview", false);
  refreshSecurityState();
}

function bindNodes() {
  for (const id of [
    "connection-card", "connection-dot", "connection-label", "connection-detail", "plane-navigation",
    "refresh-button", "last-refresh", "plane-label", "surface-title", "surface-subtitle", "active-context",
    "open-chat-button", "alert-region", "surface-toolbar", "surface-content", "security-mode", "security-kills",
    "emergency-controls", "confirm-dialog", "confirm-title", "confirm-copy", "confirm-action"
  ]) nodes[id] = document.getElementById(id);
}

function isValidBridge(candidate) {
  return Boolean(candidate && typeof candidate.read === "function");
}

function renderNavigation() {
  nodes["plane-navigation"].replaceChildren();
  for (const [plane, items] of Object.entries(PLANES)) {
    const group = el("section", "plane-group");
    group.dataset.plane = plane;
    group.append(el("div", "plane-heading", plane));
    for (const item of items) {
      const button = el("button", "nav-button");
      button.type = "button";
      button.dataset.surface = item.id;
      button.append(el("span", "", item.label));
      if (item.source === "host") button.append(el("span", "external-mark", "HOST"));
      group.append(button);
    }
    nodes["plane-navigation"].append(group);
  }
}

function bindStaticEvents() {
  nodes["plane-navigation"].addEventListener("click", event => {
    const button = event.target.closest("button[data-surface]");
    if (button) selectSurface(button.dataset.surface, true);
  });
  nodes["refresh-button"].addEventListener("click", () => refreshCurrent());
  nodes["open-chat-button"].addEventListener("click", () => openChat(state.context));
  nodes["emergency-controls"].addEventListener("click", event => {
    const button = event.target.closest("button[data-emergency]");
    if (button) confirmEmergency(button.dataset.emergency);
  });
  nodes["confirm-action"].addEventListener("click", event => {
    event.preventDefault();
    const operation = nodes["confirm-action"].dataset.operation;
    nodes["confirm-dialog"].close();
    if (operation) executeEmergency(operation);
  });
}

async function selectSurface(surfaceId, userInitiated) {
  const item = ALL_SURFACES.get(surfaceId);
  if (!item) return;
  state.currentSurface = surfaceId;
  state.search = "";
  markNavigation(surfaceId);
  nodes["plane-label"].textContent = item.plane;
  nodes["surface-title"].textContent = item.label;
  nodes["surface-subtitle"].textContent = item.subtitle;
  renderToolbar(item);
  if (!state.bridge) return renderDisconnected();

  if (item.source === "host") {
    renderHostSurface(item);
    if (userInitiated && typeof state.bridge.navigate === "function") {
      try { await state.bridge.navigate(item.id, {context: state.context}); }
      catch (error) { showAlert("error", normalizeError(error)); }
    }
    return;
  }
  await loadReadSurface(item.id);
}

async function refreshCurrent() {
  const current = ALL_SURFACES.get(state.currentSurface);
  if (!current || !state.bridge) return;
  if (current.source === "host") return renderHostSurface(current);
  await loadReadSurface(current.id);
  if (current.id !== "security") await refreshSecurityState();
}

async function loadReadSurface(surfaceId) {
  setLoading();
  clearAlerts();
  try {
    const params = {pageSize: 50};
    if (SEARCHABLE.has(surfaceId) && state.search) params.q = state.search;
    if (surfaceId === "personality") {
      const [personality, identity] = await Promise.all([
        state.bridge.read("personality", params),
        state.bridge.read("identity", {})
      ]);
      renderPersonality(personality, identity);
    } else {
      const payload = await state.bridge.read(surfaceId, params);
      renderPayload(surfaceId, payload);
    }
    stampRefresh();
  } catch (error) {
    renderError(error);
  }
}

function renderPayload(surfaceId, payload) {
  if (surfaceId === "overview") return renderOverview(payload);
  if (surfaceId === "security") updateSecurityRail(payload);
  if (surfaceId === "personality") return renderPersonality(payload);
  if (payload && Array.isArray(payload.items)) return renderCollection(surfaceId, payload.items, payload);
  if (payload && typeof payload === "object") return renderResource(surfaceId, payload);
  renderEmpty("No authorized data is available for this surface.");
}

function renderOverview(payload) {
  const attrs = objectAttributes(payload);
  nodes["surface-content"].replaceChildren();
  const metrics = el("div", "metric-grid");
  const cards = [
    ["Health", attrs.healthy === true ? "Healthy" : "Attention", attrs.operationalMode ?? "mode unknown"],
    ["Active tasks", attrs.activeTasks ?? 0, `${attrs.waitingTasks ?? 0} waiting`],
    ["Owner decisions", attrs.ownerDecisionsRequired ?? attrs.selfDevelopmentWaiting ?? 0, "approval / dependency queue"],
    ["Automations", attrs.enabledAutomations ?? 0, `${attrs.automationFailures ?? 0} recent failures`],
    ["Autonomous actions", attrs.autonomousActions ?? 0, "recent bounded actions"],
    ["Failed tasks", attrs.failedTasks ?? 0, "current task heads"],
    ["Personality revision", attrs.personalityRevision ?? "—", attrs.personalityRevisionId ?? "current state"],
    ["Runtime", attrs.runtimeVersion ?? attrs.foundationVersion ?? "—", attrs.currentActivity ?? "Idle"]
  ];
  for (const [label, value, detail] of cards) metrics.append(metricCard(label, value, detail));
  nodes["surface-content"].append(metrics);

  const detail = el("div", "card-grid");
  detail.append(resourceCard("Current state", payload?.id ?? "buggy-overview", attrs, false));
  detail.append(ownerBriefCard(attrs));
  nodes["surface-content"].append(detail);
}

function ownerBriefCard(attrs) {
  const card = el("article", "card");
  const header = el("div", "card-header");
  const titleWrap = el("div");
  titleWrap.append(el("div", "card-id", "OWNER BRIEF"), el("div", "card-title", "What needs attention"));
  header.append(titleWrap, badge(attentionTone(attrs), attentionLabel(attrs)));
  card.append(header);
  const list = el("dl", "kv-list");
  appendKv(list, "Doing now", attrs.currentActivity ?? "Idle");
  appendKv(list, "Decisions", attrs.ownerDecisionsRequired ?? attrs.selfDevelopmentWaiting ?? 0);
  appendKv(list, "Incidents", attrs.incidentCount ?? attrs.openIncidents ?? 0);
  appendKv(list, "Failures", Number(attrs.failedTasks ?? 0) + Number(attrs.automationFailures ?? 0));
  appendKv(list, "Mode", attrs.operationalMode ?? "Unknown");
  card.append(list);
  return card;
}

function renderPersonality(payload, identityPayload = null) {
  const attrs = objectAttributes(payload);
  const identity = objectAttributes(identityPayload);
  nodes["surface-content"].replaceChildren();
  const grid = el("div", "metric-grid");
  grid.append(
    metricCard("Identity", identity.displayName ?? identity.assistantId ?? "BUGGY", "Protected Identity Core"),
    metricCard("Seed", attrs.seedVersion ?? "—", "immutable starting profile"),
    metricCard("Current revision", attrs.currentRevision ?? attrs.revision ?? "—", attrs.revisionId ?? ""),
    metricCard("Confidence", formatBps(attrs.confidenceBps), attrs.origin ?? "origin unknown")
  );
  const detail = el("div", "card-grid");
  detail.append(
    resourceCard("Identity Core", identityPayload?.id ?? "buggy-identity", identity, false),
    resourceCard("Personality state", payload?.id ?? "buggy-personality", attrs, false)
  );
  nodes["surface-content"].append(grid, detail);
}

function renderCollection(surfaceId, items, page) {
  nodes["surface-content"].replaceChildren();
  if (!items.length) return renderEmpty("No authorized items found.");
  const grid = el("div", "card-grid");
  for (const item of items) {
    const attrs = objectAttributes(item);
    const card = resourceCard(preferredTitle(surfaceId, attrs), item.id ?? "item", attrs, CONTEXT_SURFACES.has(surfaceId));
    if (surfaceId === "approvals") card.classList.add("approval-card");
    grid.append(card);
  }
  nodes["surface-content"].append(grid);
  if (page?.hasMore) showAlert("warning", "More results are available. The Panel Backend returned a bounded page.");
}

function renderResource(surfaceId, payload) {
  nodes["surface-content"].replaceChildren(resourceCard(surfaceLabel(surfaceId), payload.id ?? surfaceId, objectAttributes(payload), CONTEXT_SURFACES.has(surfaceId)));
}

function resourceCard(title, id, attrs, chatAction) {
  const card = el("article", "card");
  const header = el("div", "card-header");
  const titleWrap = el("div");
  titleWrap.append(el("div", "card-id", String(id)), el("div", "card-title", String(title || id)));
  header.append(titleWrap);
  const status = attrs.status ?? attrs.state ?? attrs.risk ?? attrs.attention;
  if (status != null) header.append(badge(toneFor(status), String(status)));
  card.append(header);
  const list = el("dl", "kv-list");
  for (const [key, value] of orderedEntries(attrs)) appendKv(list, humanize(key), formatValue(value));
  card.append(list);
  if (chatAction) {
    const actions = el("div", "card-actions");
    const button = el("button", "secondary-button", "Open in Chat");
    button.type = "button";
    button.addEventListener("click", () => openChat({type: state.currentSurface, id: String(id)}));
    actions.append(button);
    card.append(actions);
  }
  return card;
}

function renderToolbar(item) {
  nodes["surface-toolbar"].replaceChildren();
  if (!SEARCHABLE.has(item.id)) {
    nodes["surface-toolbar"].hidden = true;
    return;
  }
  nodes["surface-toolbar"].hidden = false;
  const input = el("input", "search-input");
  input.type = "search";
  input.maxLength = 256;
  input.placeholder = item.id === "audit" ? "Search immutable audit trail" : "Search Buggy knowledge";
  input.setAttribute("aria-label", input.placeholder);
  const button = el("button", "ghost-button", "Search");
  button.type = "button";
  const run = () => {
    state.search = input.value.trim();
    loadReadSurface(item.id);
  };
  button.addEventListener("click", run);
  input.addEventListener("keydown", event => { if (event.key === "Enter") run(); });
  nodes["surface-toolbar"].append(input, button);
}

function renderHostSurface(item) {
  nodes["surface-content"].replaceChildren();
  const card = el("section", "host-card");
  card.append(el("div", "eyebrow", "PANEL HOST SURFACE"), el("h2", "", item.label));
  const copy = item.id === "chat"
    ? "Context-aware Chat is hosted by the existing Panel conversation surface. Active context is handed to the Panel Backend bridge without granting browser authority."
    : `${item.label} is owned by the broader Legit Panel/Foundation surface. Buggy Control Center links into it rather than duplicating its backend or authority.`;
  card.append(el("p", "", copy));
  const button = el("button", "secondary-button", item.id === "chat" ? "Open Chat" : `Open ${item.label}`);
  button.type = "button";
  button.disabled = typeof state.bridge?.navigate !== "function" && item.id !== "chat";
  button.addEventListener("click", () => item.id === "chat" ? openChat(state.context) : state.bridge?.navigate?.(item.id, {context: state.context}));
  card.append(button);
  nodes["surface-content"].append(card);
}

function renderDisconnected() {
  nodes["surface-content"].replaceChildren();
  const card = el("section", "empty-state");
  card.append(el("strong", "", "Panel Backend connection required"), el("p", "", "This frontend intentionally has no direct Assistant API transport. Install the trusted Panel Backend bridge to load authorized data."));
  nodes["surface-content"].append(card);
}

function setLoading() {
  nodes["surface-content"].replaceChildren(el("div", "loading-card", "Loading authorized view from Panel Backend…"));
}

function renderEmpty(message) {
  nodes["surface-content"].replaceChildren(el("div", "empty-state", message));
}

function renderError(error) {
  nodes["surface-content"].replaceChildren();
  showAlert("error", normalizeError(error));
  const card = el("section", "empty-state");
  card.append(el("strong", "", "Surface unavailable"), el("p", "", "The Panel Backend denied or could not resolve this view. No fallback data was fabricated."));
  nodes["surface-content"].append(card);
}

function attachRealtime() {
  if (typeof state.bridge.subscribe !== "function") return;
  try {
    state.subscription = state.bridge.subscribe(event => {
      const type = String(event?.type ?? "");
      if (!type.startsWith("BUGGY_") && !type.startsWith("ASSISTANT_")) return;
      scheduleRefresh(type);
    });
  } catch (error) {
    showAlert("warning", `Realtime unavailable: ${normalizeError(error)}`);
  }
}

function scheduleRefresh(type) {
  if (state.refreshTimer != null) window.clearTimeout(state.refreshTimer);
  state.refreshTimer = window.setTimeout(() => {
    state.refreshTimer = null;
    if (eventTouchesSurface(type, state.currentSurface)) refreshCurrent();
    if (type.includes("SECURITY") || type.includes("MODE")) refreshSecurityState();
  }, 250);
}

function eventTouchesSurface(type, surfaceId) {
  if (type.includes("TASK")) return surfaceId === "tasks" || surfaceId === "overview";
  if (type.includes("ACTIVITY")) return surfaceId === "activity" || surfaceId === "overview";
  if (type.includes("AUTOMATION")) return surfaceId === "automations" || surfaceId === "overview";
  if (type.includes("SELF_DEVELOPMENT")) return surfaceId === "self-development" || surfaceId === "overview";
  if (type.includes("OWNER_DECISION") || type.includes("APPROVAL")) return surfaceId === "approvals" || surfaceId === "overview";
  return true;
}

async function refreshSecurityState() {
  if (!state.bridge) return;
  try {
    const payload = await state.bridge.read("security", {});
    updateSecurityRail(payload);
  } catch (_) {
    nodes["security-mode"].textContent = "Unavailable";
    nodes["security-kills"].textContent = "Unavailable";
  }
}

function updateSecurityRail(payload) {
  const attrs = objectAttributes(payload);
  nodes["security-mode"].textContent = String(attrs.operationalMode ?? "Unknown");
  const switches = Array.isArray(attrs.activeKillSwitches) ? attrs.activeKillSwitches : [];
  nodes["security-kills"].textContent = switches.length ? switches.join(", ") : "None";
}

function confirmEmergency(operation) {
  if (!state.bridge || typeof state.bridge.command !== "function") return;
  const label = EMERGENCY_LABELS[operation];
  if (!label) return;
  nodes["confirm-title"].textContent = label;
  nodes["confirm-copy"].textContent = `${label} is a Foundation-owned Owner control. The Panel Backend must authorize and route it through the normal pipeline; Buggy cannot self-approve it.`;
  nodes["confirm-action"].dataset.operation = operation;
  nodes["confirm-dialog"].showModal();
}

async function executeEmergency(operation) {
  clearAlerts();
  disableEmergencyControls(true);
  try {
    await state.bridge.command(operation, {assistantId: "buggy", source: "buggy-control-center-v1"});
    showAlert("success", `${EMERGENCY_LABELS[operation]} was submitted to the Panel Backend. Final state is determined by Foundation authorization and pipeline execution.`);
    await refreshSecurityState();
    if (state.currentSurface === "security" || state.currentSurface === "overview") await refreshCurrent();
  } catch (error) {
    showAlert("error", `Emergency control not applied: ${normalizeError(error)}`);
  } finally {
    disableEmergencyControls(false);
  }
}

async function openChat(context) {
  if (!state.bridge) return;
  try {
    if (typeof state.bridge.openChat === "function") await state.bridge.openChat(context ?? null);
    else if (typeof state.bridge.navigate === "function") await state.bridge.navigate("chat", {context: context ?? null});
    else showAlert("warning", "The Panel host has not provided a Chat navigation bridge.");
  } catch (error) {
    showAlert("error", normalizeError(error));
  }
}

function readInitialContext() {
  const params = new URLSearchParams(window.location.search);
  const type = cleanToken(params.get("contextType"), 48);
  const id = cleanToken(params.get("contextId"), 192);
  return type && id ? Object.freeze({type, id}) : null;
}

function renderContext() {
  if (!state.context) {
    nodes["active-context"].hidden = true;
    nodes["open-chat-button"].textContent = "Open Chat";
    return;
  }
  nodes["active-context"].hidden = false;
  nodes["active-context"].textContent = `${state.context.type}: ${state.context.id}`;
  nodes["open-chat-button"].textContent = "Open context in Chat";
}

function cleanToken(value, maxLength) {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  if (!trimmed || trimmed.length > maxLength || !/^[A-Za-z0-9._:@\/-]+$/.test(trimmed)) return null;
  return trimmed;
}

function markNavigation(surfaceId) {
  for (const button of nodes["plane-navigation"].querySelectorAll("button[data-surface]")) {
    const active = button.dataset.surface === surfaceId;
    button.classList.toggle("active", active);
    if (active) button.setAttribute("aria-current", "page"); else button.removeAttribute("aria-current");
  }
}

function setConnection(connected, label, detail) {
  nodes["connection-label"].textContent = label;
  nodes["connection-detail"].textContent = detail;
  nodes["connection-dot"].classList.toggle("connected", connected);
  nodes["connection-dot"].classList.toggle("disconnected", !connected);
}

function disableEmergencyControls(disabled) {
  for (const button of nodes["emergency-controls"].querySelectorAll("button[data-emergency]")) button.disabled = disabled;
}

function showAlert(level, message) {
  const alert = el("div", `alert ${level}`, message);
  nodes["alert-region"].replaceChildren(alert);
}

function clearAlerts() { nodes["alert-region"].replaceChildren(); }
function stampRefresh() { nodes["last-refresh"].textContent = `Refreshed ${new Date().toLocaleTimeString()}`; }

function el(tag, className = "", text = null) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== null && text !== undefined) node.textContent = String(text);
  return node;
}

function metricCard(label, value, detail) {
  const card = el("article", "metric-card");
  card.append(el("div", "metric-label", label), el("div", "metric-value", formatValue(value)), el("div", "metric-detail", detail));
  return card;
}

function badge(tone, text) { return el("span", `badge ${tone}`, text); }

function appendKv(list, key, value) {
  const row = el("div", "kv-row");
  row.append(el("dt", "kv-key", key), el("dd", "kv-value", value));
  list.append(row);
}

function orderedEntries(attrs) {
  const priority = ["status", "state", "risk", "attention", "requestedAction", "environment", "resource", "reason", "relatedBug", "relatedIncident", "artifact", "diff", "tests", "security", "rollbackAvailable", "expectedImpact", "downtime"];
  const rank = new Map(priority.map((key, index) => [key, index]));
  return Object.entries(attrs).sort(([a], [b]) => (rank.get(a) ?? 1000) - (rank.get(b) ?? 1000) || a.localeCompare(b));
}

function objectAttributes(payload) {
  return payload && typeof payload.attributes === "object" && payload.attributes !== null ? payload.attributes : {};
}

function preferredTitle(surfaceId, attrs) {
  return attrs.title ?? attrs.name ?? attrs.summary ?? attrs.requestedAction ?? attrs.action ?? surfaceLabel(surfaceId);
}

function surfaceLabel(surfaceId) { return ALL_SURFACES.get(surfaceId)?.label ?? humanize(surfaceId); }

function humanize(value) {
  return String(value).replace(/([a-z0-9])([A-Z])/g, "$1 $2").replace(/[_-]+/g, " ").replace(/^./, char => char.toUpperCase());
}

function formatValue(value) {
  if (value === null || value === undefined || value === "") return "—";
  if (Array.isArray(value)) return value.length ? value.map(formatValue).join(", ") : "None";
  if (typeof value === "object") return Object.entries(value).map(([key, item]) => `${humanize(key)}: ${formatValue(item)}`).join(" · ");
  if (typeof value === "boolean") return value ? "Yes" : "No";
  return String(value);
}

function formatBps(value) {
  const number = Number(value);
  return Number.isFinite(number) ? `${(number / 100).toFixed(2)}%` : "—";
}

function toneFor(value) {
  const text = String(value).toUpperCase();
  if (["HEALTHY", "ACTIVE", "ENABLED", "NORMAL", "COMPLETED", "DEPLOYED", "APPROVED", "LOW"].some(token => text.includes(token))) return "good";
  if (["FAILED", "CRITICAL", "URGENT", "DENIED", "DISABLED", "PAUSED", "HIGH"].some(token => text.includes(token))) return "bad";
  return "warn";
}

function attentionTone(attrs) {
  const count = Number(attrs.ownerDecisionsRequired ?? attrs.selfDevelopmentWaiting ?? 0) + Number(attrs.failedTasks ?? 0) + Number(attrs.automationFailures ?? 0);
  return count > 0 ? "warn" : "good";
}
function attentionLabel(attrs) { return attentionTone(attrs) === "good" ? "Clear" : "Review"; }

function normalizeError(error) {
  const message = error instanceof Error ? error.message : String(error ?? "Unknown Panel Backend error");
  return message.length > 400 ? `${message.slice(0, 397)}…` : message;
}
