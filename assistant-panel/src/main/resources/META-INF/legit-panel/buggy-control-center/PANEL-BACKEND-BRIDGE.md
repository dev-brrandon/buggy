# Buggy Control Center Panel Backend Bridge

B9 is a browser bundle, not a security boundary. The browser MUST NOT call the Assistant API directly.

The owning Legit Panel page provides `window.LegitPanelAssistantBridge.buggy` before loading the B9 module.

Required method:

```text
read(surfaceId, params) -> Promise<ResourceView | ResourcePage>
```

Optional methods:

```text
subscribe(listener) -> unsubscribe handle
command(operation, payload) -> Promise<Result>
navigate(surfaceId, { context }) -> Promise|void
openChat(context) -> Promise|void
```

Security contract:

- `read` is implemented by the trusted Panel Backend and may proxy B8 only after Phase-15 request signing and server-side authorization.
- `command` MUST map logical UI intents into the existing Phase-13/Phase-15 pipeline. It MUST NOT become a direct SecurityControlPlane mutator.
- browser state, hidden buttons, route names or supplied context are never proof of authorization.
- the Panel Backend re-authorizes every request and mutation.
- SSE/realtime data is sanitized and bounded by the existing Phase-15 realtime contract before delivery to the browser bridge.
- the frontend intentionally contains no `fetch`, `XMLHttpRequest`, `EventSource`, `WebSocket` or direct `/api/assistant/v1` transport.
- audit data is rendered read-only.
- Identity Core is rendered read-only.
- emergency controls are visually separate and remain Foundation-owned.

Emergency logical operations:

```text
buggy.emergency.pause
buggy.emergency.read-only
buggy.emergency.disable-automations
buggy.emergency.disable-self-healing
buggy.emergency.disable-tools
buggy.emergency.maintenance
```

These names are UI intents only. The Panel Backend maps them to canonical Foundation operations and rejects unsupported or unauthorized requests fail-closed.
