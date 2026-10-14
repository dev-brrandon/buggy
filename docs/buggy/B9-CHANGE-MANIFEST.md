# B9 Change Manifest

## New Owner-facing frontend bundle

- `assistant-panel/src/main/resources/META-INF/legit-panel/buggy-control-center/index.html`
- `assistant-panel/src/main/resources/META-INF/legit-panel/buggy-control-center/assets/control-center.css`
- `assistant-panel/src/main/resources/META-INF/legit-panel/buggy-control-center/assets/control-center.js`
- `assistant-panel/src/main/resources/META-INF/legit-panel/buggy-control-center/manifest.json`
- `assistant-panel/src/main/resources/META-INF/legit-panel/buggy-control-center/PANEL-BACKEND-BRIDGE.md`

## Verification

- `assistant-panel/src/selfTest/java/de/legitserver/assistant/panel/B9BuggyControlCenterFrontendSelfTest.java`
- `assistant-panel/build.gradle` adds `buggyControlCenterFrontendSelfTest`

## Documentation

- `docs/buggy/B9-BUGGY-CONTROL-CENTER-FRONTEND-IMPLEMENTATION-REPORT-2026-10-05.md`
- `docs/buggy/B9-CHANGE-MANIFEST.md`
- `README.md` Buggy track moved to B9 complete / B10 next

## Authority impact

None.

B9 does not add a database migration, write store, Assistant API POST route, direct browser Assistant API transport, security authority, approval state machine or direct emergency-control mutator.
