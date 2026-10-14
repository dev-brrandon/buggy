# B8 Change Manifest

## New Control Center domain projection

- `assistant-buggy/.../controlcenter/BuggyControlCenterResource.java`
- `assistant-buggy/.../controlcenter/BuggyControlCenterSurface.java`
- `assistant-buggy/.../controlcenter/BuggyControlCenterReadService.java`

## Additive repository reads

- `BuggyTaskRepository.listCurrentTasks(int)` + JDBC implementation
- `BuggyAutomationRepository.listCurrentAutomations(int)` + JDBC implementation
- `BuggySelfDevelopmentRepository.listCurrentItems(int)` + JDBC implementation

These are bounded reads over existing B6/B7 state; B8 adds no persistence schema.

## New secured API backend

- `BuggyControlCenterBackend`
- `SecuredBuggyControlCenterBackend`
- `UnavailableBuggyControlCenterBackend`
- `BuggyControlCenterExternalReadPort`
- `BuggyControlCenterConfiguration`
- `BuggyControlCenterController`
- `BuggyControlCenterOpenApiController`
- `BuggyControlCenterEventPublisher`
- `openapi/buggy-control-center-v1.yaml`

## Existing Panel realtime extension

`PanelRealtimeEventType` gains bounded Buggy notification types only. The existing `PanelRealtimeHub` and `/api/assistant/v1/panel/events` SSE transport remain authoritative for realtime delivery.

## Verification additions

- `B8BuggyControlCenterBackendSelfTest`
- `B8BuggyControlCenterApiSelfTest`
- `assistant-buggy:controlCenterSelfTest`
- `assistant-api:buggyControlCenterSelfTest`

## Explicitly unchanged

- Foundation Security authority
- NetworkCore permission authority
- Request/Approval authority
- Tool Runtime
- Knowledge publication authority
- Audit immutability
- Phase-13 mutation pipeline
- Phase-15 Panel trust boundary
- PostgreSQL schema/migrations
- BYTE implementation
- KAESE implementation
