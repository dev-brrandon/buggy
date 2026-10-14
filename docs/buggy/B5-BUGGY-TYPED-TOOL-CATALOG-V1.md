# BUGGY B5 Typed Tool Catalog V1

Catalog ID: `buggy-tools-v1`
Foundation runtime: Phase 10 `ToolRegistry` / `ToolDescriptor`
Security profile: `buggy-capabilities-v1`
Total initial tools: 146

| Category | Tool ID | Capability | Risk | Policy | Allowed resource types |
|---|---|---|---|---|---|
| DIAGNOSTICS | `tool.diagnostics.service.status` | `assistant.buggy.diagnostics.read` | LOW | AUTONOMOUS_LOW | `service` |
| DIAGNOSTICS | `tool.diagnostics.service.health` | `assistant.buggy.diagnostics.read` | LOW | AUTONOMOUS_LOW | `service` |
| DIAGNOSTICS | `tool.diagnostics.logs.search` | `assistant.buggy.diagnostics.read` | LOW | AUTONOMOUS_LOW | `log` |
| DIAGNOSTICS | `tool.diagnostics.logs.tail` | `assistant.buggy.diagnostics.execute` | MEDIUM | FOUNDATION_POLICY_GATED | `log` |
| DIAGNOSTICS | `tool.diagnostics.logs.correlate` | `assistant.buggy.diagnostics.execute` | MEDIUM | FOUNDATION_POLICY_GATED | `log` |
| DIAGNOSTICS | `tool.diagnostics.resource.cpu` | `assistant.buggy.diagnostics.read` | LOW | AUTONOMOUS_LOW | `host`, `service`, `container` |
| DIAGNOSTICS | `tool.diagnostics.resource.memory` | `assistant.buggy.diagnostics.read` | LOW | AUTONOMOUS_LOW | `host`, `service`, `container` |
| DIAGNOSTICS | `tool.diagnostics.resource.disk` | `assistant.buggy.diagnostics.read` | LOW | AUTONOMOUS_LOW | `host`, `service`, `container` |
| DIAGNOSTICS | `tool.diagnostics.resource.network` | `assistant.buggy.diagnostics.read` | LOW | AUTONOMOUS_LOW | `host`, `service`, `container` |
| DIAGNOSTICS | `tool.diagnostics.dependency.check` | `assistant.buggy.diagnostics.execute` | MEDIUM | FOUNDATION_POLICY_GATED | `dependency`, `service` |
| DIAGNOSTICS | `tool.diagnostics.minecraft.server` | `assistant.buggy.diagnostics.read` | LOW | AUTONOMOUS_LOW | `minecraft-server` |
| DIAGNOSTICS | `tool.diagnostics.velocity.proxy` | `assistant.buggy.diagnostics.read` | LOW | AUTONOMOUS_LOW | `velocity-proxy` |
| DIAGNOSTICS | `tool.diagnostics.postgres.health` | `assistant.buggy.diagnostics.execute` | MEDIUM | FOUNDATION_POLICY_GATED | `postgres-cluster` |
| DIAGNOSTICS | `tool.diagnostics.container.inspect` | `assistant.buggy.diagnostics.read` | LOW | AUTONOMOUS_LOW | `container` |
| SOURCE | `tool.source.read` | `assistant.buggy.source.read` | HIGH | FOUNDATION_POLICY_GATED | `source-repository` |
| SOURCE | `tool.source.search` | `assistant.buggy.source.read` | HIGH | FOUNDATION_POLICY_GATED | `source-repository` |
| SOURCE | `tool.source.diff` | `assistant.buggy.source.read` | HIGH | FOUNDATION_POLICY_GATED | `source-repository` |
| SOURCE | `tool.source.history` | `assistant.buggy.source.read` | HIGH | FOUNDATION_POLICY_GATED | `source-repository` |
| SOURCE | `tool.source.dependencies` | `assistant.buggy.source.read` | HIGH | FOUNDATION_POLICY_GATED | `source-repository` |
| SOURCE | `tool.source.analyze` | `assistant.buggy.source.read` | HIGH | FOUNDATION_POLICY_GATED | `source-repository` |
| SOURCE | `tool.source.create-workspace` | `assistant.buggy.source.workspace.mutate` | MEDIUM | FOUNDATION_POLICY_GATED | `source-workspace` |
| SOURCE | `tool.source.modify-workspace` | `assistant.buggy.source.workspace.mutate` | MEDIUM | FOUNDATION_POLICY_GATED | `source-workspace` |
| SOURCE | `tool.source.create-patch` | `assistant.buggy.source.workspace.mutate` | MEDIUM | FOUNDATION_POLICY_GATED | `source-workspace` |
| SOURCE | `tool.source.compare-builds` | `assistant.buggy.source.read` | HIGH | FOUNDATION_POLICY_GATED | `build-artifact` |
| SOURCE | `tool.source.static-analysis` | `assistant.buggy.source.read` | HIGH | FOUNDATION_POLICY_GATED | `source-repository`, `source-workspace` |
| BUILD | `tool.build.gradle` | `assistant.buggy.build.execute` | LOW | AUTONOMOUS_LOW | `build-workspace`, `build-artifact` |
| BUILD | `tool.build.maven` | `assistant.buggy.build.execute` | LOW | AUTONOMOUS_LOW | `build-workspace`, `build-artifact` |
| BUILD | `tool.build.java` | `assistant.buggy.build.execute` | LOW | AUTONOMOUS_LOW | `build-workspace`, `build-artifact` |
| BUILD | `tool.build.test` | `assistant.buggy.build.execute` | LOW | AUTONOMOUS_LOW | `build-workspace`, `build-artifact` |
| BUILD | `tool.build.integration-test` | `assistant.buggy.build.execute` | LOW | AUTONOMOUS_LOW | `build-workspace`, `build-artifact` |
| BUILD | `tool.build.security-test` | `assistant.buggy.build.execute` | LOW | AUTONOMOUS_LOW | `build-workspace`, `build-artifact` |
| BUILD | `tool.build.benchmark` | `assistant.buggy.build.execute` | LOW | AUTONOMOUS_LOW | `build-workspace`, `build-artifact` |
| BUILD | `tool.build.jdeps` | `assistant.buggy.build.execute` | LOW | AUTONOMOUS_LOW | `build-workspace`, `build-artifact` |
| BUILD | `tool.build.package` | `assistant.buggy.build.execute` | LOW | AUTONOMOUS_LOW | `build-workspace`, `build-artifact` |
| BUILD | `tool.build.verify-artifact` | `assistant.buggy.build.execute` | LOW | AUTONOMOUS_LOW | `build-workspace`, `build-artifact` |
| BUILD | `tool.build.sha256` | `assistant.buggy.build.execute` | LOW | AUTONOMOUS_LOW | `build-workspace`, `build-artifact` |
| DEPLOYMENT | `tool.deployment.inspect` | `assistant.buggy.deployment.read` | MEDIUM | FOUNDATION_POLICY_GATED | `deployment` |
| DEPLOYMENT | `tool.deployment.plan` | `assistant.buggy.deployment.read` | MEDIUM | FOUNDATION_POLICY_GATED | `deployment-target` |
| DEPLOYMENT | `tool.deployment.stage` | `assistant.buggy.deployment.nonprod.execute` | MEDIUM | FOUNDATION_POLICY_GATED | `deployment-nonprod` |
| DEPLOYMENT | `tool.deployment.verify` | `assistant.buggy.deployment.read` | MEDIUM | FOUNDATION_POLICY_GATED | `deployment` |
| DEPLOYMENT | `tool.deployment.rollback` | `assistant.buggy.deployment.production.request` | MEDIUM | REQUEST_ONLY | `deployment` |
| DEPLOYMENT | `tool.deployment.production` | `assistant.buggy.deployment.production.request` | MEDIUM | REQUEST_ONLY | `deployment-production` |
| DEPLOYMENT | `tool.deployment.status` | `assistant.buggy.deployment.read` | MEDIUM | FOUNDATION_POLICY_GATED | `deployment` |
| DEPLOYMENT | `tool.deployment.compare` | `assistant.buggy.deployment.read` | MEDIUM | FOUNDATION_POLICY_GATED | `deployment` |
| INFRASTRUCTURE | `tool.infrastructure.host.status` | `assistant.buggy.infrastructure.read` | MEDIUM | FOUNDATION_POLICY_GATED | `host` |
| INFRASTRUCTURE | `tool.infrastructure.container.status` | `assistant.buggy.infrastructure.read` | MEDIUM | FOUNDATION_POLICY_GATED | `container` |
| INFRASTRUCTURE | `tool.infrastructure.container.restart` | `assistant.buggy.infrastructure.recovery.low` | LOW | AUTONOMOUS_LOW | `container` |
| INFRASTRUCTURE | `tool.infrastructure.service.restart` | `assistant.buggy.infrastructure.recovery.low` | LOW | AUTONOMOUS_LOW | `service` |
| INFRASTRUCTURE | `tool.infrastructure.disk.inspect` | `assistant.buggy.infrastructure.read` | MEDIUM | FOUNDATION_POLICY_GATED | `host` |
| INFRASTRUCTURE | `tool.infrastructure.network.inspect` | `assistant.buggy.infrastructure.read` | MEDIUM | FOUNDATION_POLICY_GATED | `host` |
| INFRASTRUCTURE | `tool.infrastructure.resource.inspect` | `assistant.buggy.infrastructure.read` | MEDIUM | FOUNDATION_POLICY_GATED | `host`, `service`, `container` |
| INFRASTRUCTURE | `tool.infrastructure.config.inspect` | `assistant.buggy.infrastructure.read` | MEDIUM | FOUNDATION_POLICY_GATED | `service`, `container`, `host` |
| DATABASE | `tool.database.health` | `assistant.buggy.database.read` | HIGH | FOUNDATION_POLICY_GATED | `database` |
| DATABASE | `tool.database.schema.inspect` | `assistant.buggy.database.read` | HIGH | FOUNDATION_POLICY_GATED | `database` |
| DATABASE | `tool.database.query.readonly` | `assistant.buggy.database.read` | HIGH | FOUNDATION_POLICY_GATED | `database` |
| DATABASE | `tool.database.explain` | `assistant.buggy.database.read` | HIGH | FOUNDATION_POLICY_GATED | `database` |
| DATABASE | `tool.database.performance` | `assistant.buggy.database.read` | HIGH | FOUNDATION_POLICY_GATED | `database` |
| DATABASE | `tool.database.connections` | `assistant.buggy.database.read` | HIGH | FOUNDATION_POLICY_GATED | `database` |
| DATABASE | `tool.database.locks` | `assistant.buggy.database.read` | HIGH | FOUNDATION_POLICY_GATED | `database` |
| DATABASE | `tool.database.migration.inspect` | `assistant.buggy.database.read` | HIGH | FOUNDATION_POLICY_GATED | `database` |
| DATABASE | `tool.database.backup` | `assistant.buggy.database.write.request` | MEDIUM | REQUEST_ONLY | `database` |
| DATABASE | `tool.database.verify-backup` | `assistant.buggy.database.read` | HIGH | FOUNDATION_POLICY_GATED | `database` |
| DATABASE | `tool.database.restore-plan` | `assistant.buggy.database.read` | HIGH | FOUNDATION_POLICY_GATED | `database` |
| NETWORK | `tool.network.dns.inspect` | `assistant.buggy.network.read` | HIGH | FOUNDATION_POLICY_GATED | `dns-zone` |
| NETWORK | `tool.network.port.check` | `assistant.buggy.network.read` | HIGH | FOUNDATION_POLICY_GATED | `network-endpoint` |
| NETWORK | `tool.network.latency` | `assistant.buggy.network.read` | HIGH | FOUNDATION_POLICY_GATED | `network-endpoint` |
| NETWORK | `tool.network.route.inspect` | `assistant.buggy.network.read` | HIGH | FOUNDATION_POLICY_GATED | `network-endpoint` |
| NETWORK | `tool.network.tls.inspect` | `assistant.buggy.network.read` | HIGH | FOUNDATION_POLICY_GATED | `network-endpoint` |
| NETWORK | `tool.network.service-connectivity` | `assistant.buggy.network.read` | HIGH | FOUNDATION_POLICY_GATED | `service` |
| NETWORK | `tool.network.firewall.inspect` | `assistant.buggy.network.read` | HIGH | FOUNDATION_POLICY_GATED | `firewall` |
| NETWORK | `tool.network.proxy.inspect` | `assistant.buggy.network.read` | HIGH | FOUNDATION_POLICY_GATED | `proxy` |
| SECURITY | `tool.security.audit.search` | `assistant.buggy.security.read` | HIGH | FOUNDATION_POLICY_GATED | `audit` |
| SECURITY | `tool.security.permission.inspect` | `assistant.buggy.security.read` | HIGH | FOUNDATION_POLICY_GATED | `security-subject` |
| SECURITY | `tool.security.actor.inspect` | `assistant.buggy.security.read` | HIGH | FOUNDATION_POLICY_GATED | `actor` |
| SECURITY | `tool.security.capability.inspect` | `assistant.buggy.security.read` | HIGH | FOUNDATION_POLICY_GATED | `assistant` |
| SECURITY | `tool.security.session.inspect` | `assistant.buggy.security.read` | HIGH | FOUNDATION_POLICY_GATED | `security-session` |
| SECURITY | `tool.security.token.metadata` | `assistant.buggy.security.read` | HIGH | FOUNDATION_POLICY_GATED | `token-metadata` |
| SECURITY | `tool.security.policy.inspect` | `assistant.buggy.security.read` | HIGH | FOUNDATION_POLICY_GATED | `security-policy` |
| SECURITY | `tool.security.incident.inspect` | `assistant.buggy.security.read` | HIGH | FOUNDATION_POLICY_GATED | `security-incident` |
| KNOWLEDGE | `tool.knowledge.search` | `assistant.buggy.knowledge.read` | LOW | AUTONOMOUS_LOW | `knowledge-catalog` |
| KNOWLEDGE | `tool.knowledge.read` | `assistant.buggy.knowledge.read` | LOW | AUTONOMOUS_LOW | `knowledge-document` |
| KNOWLEDGE | `tool.knowledge.create-draft` | `assistant.buggy.knowledge.draft.mutate` | LOW | AUTONOMOUS_LOW | `knowledge-catalog` |
| KNOWLEDGE | `tool.knowledge.revise` | `assistant.buggy.knowledge.draft.mutate` | LOW | AUTONOMOUS_LOW | `knowledge-document` |
| KNOWLEDGE | `tool.knowledge.detect-conflicts` | `assistant.buggy.knowledge.read` | LOW | AUTONOMOUS_LOW | `knowledge-catalog` |
| KNOWLEDGE | `tool.knowledge.mark-stale` | `assistant.buggy.knowledge.draft.mutate` | LOW | AUTONOMOUS_LOW | `knowledge-document` |
| KNOWLEDGE | `tool.knowledge.attach-source` | `assistant.buggy.knowledge.draft.mutate` | LOW | AUTONOMOUS_LOW | `knowledge-document` |
| KNOWLEDGE | `tool.knowledge.request-publish` | `assistant.buggy.requests.create` | LOW | REQUEST_ONLY | `knowledge-document` |
| BUGS | `tool.bugs.search` | `assistant.buggy.bugs.read` | LOW | AUTONOMOUS_LOW | `bug` |
| BUGS | `tool.bugs.inspect` | `assistant.buggy.bugs.read` | LOW | AUTONOMOUS_LOW | `bug` |
| BUGS | `tool.bugs.create` | `assistant.buggy.bugs.mutate` | MEDIUM | FOUNDATION_POLICY_GATED | `bug` |
| BUGS | `tool.bugs.update` | `assistant.buggy.bugs.mutate` | MEDIUM | FOUNDATION_POLICY_GATED | `bug` |
| BUGS | `tool.bugs.relate` | `assistant.buggy.bugs.mutate` | MEDIUM | FOUNDATION_POLICY_GATED | `bug` |
| BUGS | `tool.bugs.duplicate` | `assistant.buggy.bugs.mutate` | MEDIUM | FOUNDATION_POLICY_GATED | `bug` |
| BUGS | `tool.bugs.regression` | `assistant.buggy.bugs.mutate` | MEDIUM | FOUNDATION_POLICY_GATED | `bug` |
| BUGS | `tool.bugs.attach-build` | `assistant.buggy.bugs.mutate` | MEDIUM | FOUNDATION_POLICY_GATED | `bug` |
| BUGS | `tool.bugs.attach-incident` | `assistant.buggy.bugs.mutate` | MEDIUM | FOUNDATION_POLICY_GATED | `bug` |
| BUGS | `tool.bugs.triage` | `assistant.buggy.bugs.mutate` | MEDIUM | FOUNDATION_POLICY_GATED | `bug` |
| BUGS | `tool.bugs.resolve` | `assistant.buggy.bugs.mutate` | MEDIUM | FOUNDATION_POLICY_GATED | `bug` |
| BUGS | `tool.bugs.reopen` | `assistant.buggy.bugs.mutate` | MEDIUM | FOUNDATION_POLICY_GATED | `bug` |
| REQUESTS | `tool.requests.create` | `assistant.buggy.requests.create` | LOW | AUTONOMOUS_LOW | `request` |
| REQUESTS | `tool.requests.inspect` | `assistant.buggy.requests.read` | LOW | AUTONOMOUS_LOW | `request` |
| REQUESTS | `tool.requests.update-payload` | `assistant.buggy.requests.create` | LOW | AUTONOMOUS_LOW | `request` |
| REQUESTS | `tool.requests.cancel` | `assistant.buggy.requests.create` | LOW | AUTONOMOUS_LOW | `request` |
| REQUESTS | `tool.requests.observe-status` | `assistant.buggy.requests.read` | LOW | AUTONOMOUS_LOW | `request` |
| SUPERVISOR | `tool.assistant.status` | `assistant.buggy.supervisor.read` | MEDIUM | FOUNDATION_POLICY_GATED | `assistant` |
| SUPERVISOR | `tool.assistant.health` | `assistant.buggy.supervisor.read` | MEDIUM | FOUNDATION_POLICY_GATED | `assistant` |
| SUPERVISOR | `tool.assistant.metrics` | `assistant.buggy.supervisor.read` | MEDIUM | FOUNDATION_POLICY_GATED | `assistant` |
| SUPERVISOR | `tool.assistant.failures` | `assistant.buggy.supervisor.read` | MEDIUM | FOUNDATION_POLICY_GATED | `assistant` |
| SUPERVISOR | `tool.assistant.degrade` | `assistant.buggy.supervisor.execute` | MEDIUM | FOUNDATION_POLICY_GATED | `assistant` |
| SUPERVISOR | `tool.assistant.restart` | `assistant.buggy.supervisor.execute` | MEDIUM | FOUNDATION_POLICY_GATED | `assistant` |
| SUPERVISOR | `tool.assistant.pause` | `assistant.buggy.supervisor.execute` | MEDIUM | FOUNDATION_POLICY_GATED | `assistant` |
| SUPERVISOR | `tool.assistant.resume` | `assistant.buggy.supervisor.execute` | MEDIUM | FOUNDATION_POLICY_GATED | `assistant` |
| SUPERVISOR | `tool.assistant.inspect-configuration` | `assistant.buggy.supervisor.read` | MEDIUM | FOUNDATION_POLICY_GATED | `assistant` |
| SUPERVISOR | `tool.assistant.inspect-tools` | `assistant.buggy.supervisor.read` | MEDIUM | FOUNDATION_POLICY_GATED | `assistant` |
| SUPERVISOR | `tool.assistant.inspect-knowledge` | `assistant.buggy.supervisor.read` | MEDIUM | FOUNDATION_POLICY_GATED | `assistant` |
| MINECRAFT | `tool.minecraft.server.status` | `assistant.buggy.minecraft.read` | LOW | AUTONOMOUS_LOW | `minecraft-server` |
| MINECRAFT | `tool.minecraft.server.health` | `assistant.buggy.minecraft.read` | LOW | AUTONOMOUS_LOW | `minecraft-server` |
| MINECRAFT | `tool.minecraft.player.inspect` | `assistant.buggy.minecraft.read` | LOW | AUTONOMOUS_LOW | `minecraft-player` |
| MINECRAFT | `tool.minecraft.world.inspect` | `assistant.buggy.minecraft.read` | LOW | AUTONOMOUS_LOW | `minecraft-world` |
| MINECRAFT | `tool.minecraft.plugin.inspect` | `assistant.buggy.minecraft.read` | LOW | AUTONOMOUS_LOW | `minecraft-server` |
| MINECRAFT | `tool.minecraft.whitelist.inspect` | `assistant.buggy.minecraft.read` | LOW | AUTONOMOUS_LOW | `minecraft-server` |
| MINECRAFT | `tool.minecraft.whitelist.add` | `assistant.buggy.minecraft.execute.low` | LOW | AUTONOMOUS_LOW | `minecraft-server` |
| MINECRAFT | `tool.minecraft.whitelist.remove` | `assistant.buggy.minecraft.execute.low` | LOW | AUTONOMOUS_LOW | `minecraft-server` |
| DISCORD | `tool.discord.guild.inspect` | `assistant.buggy.discord.read` | LOW | AUTONOMOUS_LOW | `discord-guild` |
| DISCORD | `tool.discord.member.inspect` | `assistant.buggy.discord.read` | LOW | AUTONOMOUS_LOW | `discord-member` |
| DISCORD | `tool.discord.channel.inspect` | `assistant.buggy.discord.read` | LOW | AUTONOMOUS_LOW | `discord-channel` |
| DISCORD | `tool.discord.role.inspect` | `assistant.buggy.discord.read` | LOW | AUTONOMOUS_LOW | `discord-role` |
| DISCORD | `tool.discord.message.create` | `assistant.buggy.discord.execute.low` | LOW | AUTONOMOUS_LOW | `discord-channel` |
| DISCORD | `tool.discord.message.edit-own` | `assistant.buggy.discord.execute.low` | LOW | AUTONOMOUS_LOW | `discord-channel` |
| DISCORD | `tool.discord.message.delete-own` | `assistant.buggy.discord.execute.low` | LOW | AUTONOMOUS_LOW | `discord-channel` |
| WEB | `tool.web.http.get` | `assistant.buggy.web.execute` | MEDIUM | FOUNDATION_POLICY_GATED | `web-endpoint` |
| WEB | `tool.web.http.head` | `assistant.buggy.web.execute` | MEDIUM | FOUNDATION_POLICY_GATED | `web-endpoint` |
| WEB | `tool.web.page.inspect` | `assistant.buggy.web.read` | LOW | AUTONOMOUS_LOW | `web-endpoint` |
| WEB | `tool.web.link.inspect` | `assistant.buggy.web.read` | LOW | AUTONOMOUS_LOW | `web-endpoint` |
| MAIL | `tool.mail.search` | `assistant.buggy.mail.read` | MEDIUM | FOUNDATION_POLICY_GATED | `mailbox` |
| MAIL | `tool.mail.read` | `assistant.buggy.mail.read` | MEDIUM | FOUNDATION_POLICY_GATED | `mail-message` |
| MAIL | `tool.mail.draft.prepare` | `assistant.buggy.mail.read` | MEDIUM | FOUNDATION_POLICY_GATED | `mailbox` |
| MAIL | `tool.mail.send.request` | `assistant.buggy.mail.send.request` | MEDIUM | REQUEST_ONLY | `mail-draft` |
| PANEL | `tool.panel.overview.read` | `assistant.buggy.panel.read` | MEDIUM | FOUNDATION_POLICY_GATED | `panel-overview` |
| PANEL | `tool.panel.approvals.read` | `assistant.buggy.panel.read` | MEDIUM | FOUNDATION_POLICY_GATED | `panel-approvals` |
| PANEL | `tool.panel.incidents.read` | `assistant.buggy.panel.read` | MEDIUM | FOUNDATION_POLICY_GATED | `panel-incidents` |
| PANEL | `tool.panel.knowledge.read` | `assistant.buggy.panel.read` | MEDIUM | FOUNDATION_POLICY_GATED | `panel-knowledge` |
| PANEL | `tool.panel.tools.read` | `assistant.buggy.panel.read` | MEDIUM | FOUNDATION_POLICY_GATED | `panel-tools` |
| PANEL | `tool.panel.security.read` | `assistant.buggy.panel.read` | MEDIUM | FOUNDATION_POLICY_GATED | `panel-security` |
| PANEL | `tool.panel.audit.read` | `assistant.buggy.panel.read` | MEDIUM | FOUNDATION_POLICY_GATED | `panel-audit` |
| PANEL | `tool.panel.notification.acknowledge` | `assistant.buggy.panel.execute` | MEDIUM | FOUNDATION_POLICY_GATED | `panel-notification` |

## Non-negotiable boundaries

- No `shell.execute`, `terminal.exec`, `os.command`, `docker.exec_raw`, `ssh.exec_raw`, or `sql.execute_raw` tool exists.
- Production deployment/rollback, database backup, Knowledge publish and mail send terminate at a typed `REQUEST_CREATOR` binding.
- Web handlers must bind as `BOUNDED_WEB` and enforce the canonical SSRF/redirect/size/time/content-type policy.
- `database.query.readonly` and `database.explain` must bind as `READ_ONLY_DATABASE`; no production write SQL string interface exists.
- Security tools in B5 are read-only and use only `assistant.buggy.security.read`.
- Self-Development tools are intentionally not registered in B5; their lifecycle belongs to B7.
