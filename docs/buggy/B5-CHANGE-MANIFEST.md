# B5 Change Manifest

## New Buggy production source

- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/tools/BuggyToolCategory.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/tools/BuggyToolApprovalPolicy.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/tools/BuggyToolSecretHandling.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/tools/BuggyToolDefinition.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/tools/BuggyToolSchemas.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/tools/BuggyToolCatalog.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/tools/BuggyToolHandlerKind.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/tools/BuggyToolHandlerBinding.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/tools/BuggyToolHandlerResolver.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/tools/BuggyToolRegistrar.java`
- `assistant-buggy/src/main/java/de/legitserver/assistant/buggy/tools/BuggyWebEgressPolicy.java`

## New verification

- `assistant-buggy/src/selfTest/java/de/legitserver/assistant/buggy/B5BuggyTypedToolCatalogSelfTest.java`

## Documentation

- `docs/buggy/B5-BUGGY-TYPED-TOOL-CATALOG-V1.md`
- `docs/buggy/B5-BUGGY-TYPED-TOOL-CATALOG-IMPLEMENTATION-REPORT-2026-10-04.md`
- `docs/buggy/B5-CHANGE-MANIFEST.md`

## Modified

- `assistant-buggy/build.gradle`
- `README.md`
- `docs/buggy/CURRENT-TRACK.txt`

## Persistence

- No migration added.

## Foundation changes

- No Foundation production source modified.
- Existing Tool Runtime, Security, Requests/Approvals, Audit, Knowledge, Bugs, Supervisor and Panel authorities remain authoritative.
