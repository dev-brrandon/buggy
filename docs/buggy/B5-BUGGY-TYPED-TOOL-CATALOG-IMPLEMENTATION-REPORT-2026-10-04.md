# B5 — BUGGY Typed Tool Catalog — Implementation Report

**Status:** IMPLEMENTED / SOURCE-VERIFIED
**Date:** 2026-10-04
**Foundation compatibility baseline:** `0.1.0-dev.18` — Phase 0–18 COMPLETE / ACCEPTED
**Canonical tool catalog:** `buggy-tools-v1`
**Capability profile:** `buggy-capabilities-v1`

---

## 1. Scope completed

B5 implements the initial Buggy V1 typed tool catalog strictly on top of the accepted Phase-10 Foundation Tool Runtime.

Implemented:

- 146 explicit typed tool definitions;
- all 17 categories required by the B5 implementation masterplan;
- strict input/output schemas using the existing `ToolSchema` contracts;
- one B3 capability binding for every tool;
- per-tool risk level, timeout, concurrency, classification ceiling and audit policy;
- explicit allowed resource types for every tool;
- explicit product execution policy (`AUTONOMOUS_LOW`, `FOUNDATION_POLICY_GATED`, `REQUEST_ONLY`, `OWNER_ONLY`);
- explicit secret-handling metadata;
- fail-closed explicit handler resolution;
- handler-kind enforcement for request-only, bounded Web, and read-only SQL adapters;
- canonical Web egress constraints for SSRF/redirect/size/time/content-type handling;
- resource-scope and secret-input guards before delegate handler execution;
- no generic production shell/SSH/Docker/write-SQL execution interface.

No Foundation Tool Runtime, Security, Requests/Approvals, Audit, Persistence, Pipeline or integration authority was duplicated.

---

## 2. Foundation ownership

Execution remains:

```text
Buggy typed tool definition
        ↓
explicit trusted handler binding
        ↓
Foundation ToolRegistry
        ↓
Foundation authorization
        ↓
Foundation schema validation
        ↓
Foundation classification gate
        ↓
Foundation risk / approval gate
        ↓
Foundation concurrency / timeout
        ↓
Foundation audit + secret-redacting output filter
```

B5 does not introduce a second Tool Runtime.

`BuggyToolDefinition` augments the existing Foundation `ToolDescriptor` with product metadata that Phase 10 intentionally does not own: category, description, allowed resource types, Buggy execution policy, secret handling and Owner-only marker.

---

## 3. Catalog coverage

Initial catalog size: **146 tools**.

| Category | Count |
|---|---:|
| Diagnostics | 14 |
| Source | 11 |
| Build | 11 |
| Deployment | 8 |
| Infrastructure | 8 |
| Database | 11 |
| Network | 8 |
| Security Read | 8 |
| Knowledge | 8 |
| Bugs | 12 |
| Requests | 5 |
| Supervisor | 11 |
| Minecraft | 8 |
| Discord | 7 |
| Web | 4 |
| Mail | 4 |
| Panel | 8 |

The complete descriptor inventory is recorded in `B5-BUGGY-TYPED-TOOL-CATALOG-V1.md`.

Self-Development tools from BUGGYBOT-SPEC-V1 section 7.15 are intentionally **not** pulled into B5. Their lifecycle and isolated workspace semantics belong to B7 — Self-Development Laboratory.

---

## 4. No arbitrary production shell

The B5 catalog exposes no universal production tools equivalent to:

```text
shell.execute
terminal.exec
os.command
docker.exec_raw
ssh.exec_raw
sql.execute_raw
```

There is no generic `command` or `shell` input field in any B5 tool schema.

Minecraft and Discord mutations are concrete operations, for example whitelist changes and Buggy-owned message operations. They are not command pass-throughs.

---

## 5. Request-only boundary

The following B5 tools terminate at a request-creation boundary:

```text
tool.deployment.production
tool.deployment.rollback
tool.database.backup
tool.knowledge.request-publish
tool.mail.send.request
```

A request-only tool may only bind a handler declared as:

```text
REQUEST_CREATOR
```

Binding a normal direct handler fails during catalog registration.

This preserves the B3 distinction between requesting a privileged operation and executing that operation. B5 does not create self-approval or approval-bypass capabilities.

---

## 6. Handler binding model

Every production tool requires one explicit `BuggyToolHandlerBinding`.

Supported binding kinds:

```text
DIRECT_TYPED
REQUEST_CREATOR
BOUNDED_WEB
READ_ONLY_DATABASE
```

Rules:

- normal typed operations require `DIRECT_TYPED`;
- `REQUEST_ONLY` definitions require `REQUEST_CREATOR`;
- Web category tools require `BOUNDED_WEB`;
- `database.query.readonly` and `database.explain` require `READ_ONLY_DATABASE`.

The registrar fails closed if a tool has no handler or a handler kind does not match its catalog boundary.

This is intentionally not automatic handler discovery from user text, reflection, command names or arbitrary plugin payloads.

---

## 7. Resource scope

Every B5 definition contains at least one explicit allowed `ResourceRef.resourceType`.

The Buggy registrar checks the resource type again immediately before calling the concrete handler. Foundation resource authorization remains authoritative and still runs earlier in the Phase-10 execution pipeline.

The Buggy check is defense in depth; it is not a replacement resource authorization system.

---

## 8. Secret handling

B5 reuses the Foundation `SecretGuard`.

Secret-bearing string/list input is rejected before the concrete Buggy handler runs. Phase 10 continues to redact recognized secret material from tool outputs before they cross the runtime boundary.

Security token tooling is metadata-only by design. No tool is intended to retrieve raw secret values.

---

## 9. Database boundary

B5 exposes:

```text
database.query.readonly
database.explain
```

only through the high-risk `assistant.buggy.database.read` capability and the `READ_ONLY_DATABASE` handler kind.

The concrete database adapter is required to parser-enforce a single read-only statement and bounded row count. There is no unrestricted production write SQL string tool.

Database backup is request-only in B5. Destructive restore/write execution is not exposed.

---

## 10. Web egress boundary

Web tools must bind as `BOUNDED_WEB` and implement the canonical `BuggyWebEgressPolicy`:

- schemes limited to HTTP/HTTPS;
- loopback blocked;
- private networks blocked;
- link-local blocked;
- metadata networks blocked;
- every redirect revalidated;
- at most 5 redirects;
- response size capped at 2,000,000 bytes;
- connect timeout capped at 5 seconds;
- read timeout capped at 15 seconds;
- content type allow-list required.

B5 defines and verifies this handler contract but does not introduce a generic HTTP client that would bypass it.

---

## 11. Risk and audit behavior

All descriptor risks satisfy or exceed their B3 capability security floor.

B5 contains:

```text
LOW     55
MEDIUM  57
HIGH    34
CRITICAL 0
```

Every HIGH tool uses `ToolAuditPolicy.CRITICAL_REQUIRED`, preserving Phase-10 fail-closed audit behavior and exact Phase-9 approval binding for HIGH/CRITICAL execution.

A tool descriptor never lowers the B3 capability risk floor.

---

## 12. Security Read scope

The B5 masterplan explicitly requests **Security Read**.

All eight B5 Security tools use only:

```text
assistant.buggy.security.read
```

No direct security mutation handler is registered in B5.

The B3 `assistant.buggy.security.request` capability remains available for future explicitly typed request flows, but B5 does not expose a generic security-change tool.

---

## 13. Persistence

B5 requires **no database migration**.

The tool catalog is immutable code/configuration metadata. Tool execution persistence, requests, approvals, audit and domain persistence continue to use their existing Foundation stores.

---

## 14. Verification

Local source verification completed with OpenJDK 21 because the canonical JDK-25/Gradle-9.6.1 toolchain is not available offline in this environment.

Passed:

- 510 main Java sources compiled with `-Xlint:all,-processing -Werror`;
- 33 self-test Java sources compiled with the same compiler gate;
- 9 PostgreSQL test sources compiled;
- B1 identity regression;
- B2 personality regression;
- B3 authority/capability regression;
- B3 manifest/capability binding regression;
- B4 Knowledge/Decision Memory regression;
- B4 SecretGuard integration regression;
- B5 typed tool catalog self-test;
- Phase-10 Tool Runtime regression;
- Phase-18 Foundation final verification;
- Phase-4 persistence regression;
- Vendor SHA-256 verification;
- formatting and production escape-hatch scans.

Environment-dependent gates remain pending rather than being reported as passed:

- canonical JDK 25 + Gradle 9.6.1 full quality build;
- live PostgreSQL 18.6 B1/B2/B4 regressions.

---

## 15. B5 gate result

**B5 — Typed Tool Catalog: IMPLEMENTED / SOURCE-VERIFIED**

Next canonical roadmap item:

```text
B6 — Tasks / Activity / proactive automation
```

B6 must reuse Foundation budgets, cooldowns, rate limits, audit and security controls. It must not turn the B5 catalog into an unrestricted scheduler or bypass request/approval boundaries.
