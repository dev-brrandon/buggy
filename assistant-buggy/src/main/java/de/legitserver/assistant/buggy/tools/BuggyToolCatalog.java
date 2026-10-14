package de.legitserver.assistant.buggy.tools;

import de.legitserver.assistant.core.assistant.AssistantCapabilityId;
import de.legitserver.assistant.core.classification.DataClassification;
import de.legitserver.assistant.runtime.model.ToolAuditPolicy;
import de.legitserver.assistant.runtime.model.ToolClassificationPolicy;
import de.legitserver.assistant.runtime.model.ToolDescriptor;
import de.legitserver.assistant.runtime.model.ToolId;
import de.legitserver.assistant.runtime.model.ToolRiskLevel;
import de.legitserver.assistant.runtime.schema.ToolSchema;
import de.legitserver.assistant.security.authorization.buggy.BuggySecurityCapabilities;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Canonical B5 initial typed Buggy tool catalog. Definitions are inert until explicit typed handlers are supplied. */
public final class BuggyToolCatalog {
    public static final String CATALOG_ID = "buggy-tools-v1";
    public static final BuggyWebEgressPolicy WEB_EGRESS_POLICY = BuggyWebEgressPolicy.canonical();
    private static final List<BuggyToolDefinition> DEFINITIONS = createDefinitions();
    private static final Map<ToolId, BuggyToolDefinition> BY_ID = index(DEFINITIONS);

    private BuggyToolCatalog() {}

    public static List<BuggyToolDefinition> definitions() {
        return DEFINITIONS;
    }

    public static Map<ToolId, BuggyToolDefinition> byId() {
        return BY_ID;
    }

    public static Set<BuggyToolCategory> categories() {
        EnumSet<BuggyToolCategory> categories = EnumSet.noneOf(BuggyToolCategory.class);
        for (BuggyToolDefinition definition : DEFINITIONS) {
            categories.add(definition.category());
        }
        return Set.copyOf(categories);
    }

    private static List<BuggyToolDefinition> createDefinitions() {
        ArrayList<BuggyToolDefinition> tools = new ArrayList<>();
        tools.add(tool(
                "diagnostics.service.status", BuggyToolCategory.DIAGNOSTICS, BuggySecurityCapabilities.DIAGNOSTICS_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("service"),
                "Read bounded service status.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "diagnostics.service.health", BuggyToolCategory.DIAGNOSTICS, BuggySecurityCapabilities.DIAGNOSTICS_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("service"),
                "Read bounded service health indicators.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "diagnostics.logs.search", BuggyToolCategory.DIAGNOSTICS, BuggySecurityCapabilities.DIAGNOSTICS_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.QUERY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("log"),
                "Search bounded indexed diagnostic logs.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "diagnostics.logs.tail", BuggyToolCategory.DIAGNOSTICS, BuggySecurityCapabilities.DIAGNOSTICS_EXECUTE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.LOG_TAIL, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("log"),
                "Read a bounded recent log tail without opening a shell.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "diagnostics.logs.correlate", BuggyToolCategory.DIAGNOSTICS, BuggySecurityCapabilities.DIAGNOSTICS_EXECUTE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.CORRELATION, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("log"),
                "Correlate bounded log evidence by correlation identifier.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "diagnostics.resource.cpu", BuggyToolCategory.DIAGNOSTICS, BuggySecurityCapabilities.DIAGNOSTICS_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("host", "service", "container"),
                "Read CPU telemetry for the authorized resource.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "diagnostics.resource.memory", BuggyToolCategory.DIAGNOSTICS, BuggySecurityCapabilities.DIAGNOSTICS_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("host", "service", "container"),
                "Read memory telemetry for the authorized resource.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "diagnostics.resource.disk", BuggyToolCategory.DIAGNOSTICS, BuggySecurityCapabilities.DIAGNOSTICS_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("host", "service", "container"),
                "Read disk telemetry for the authorized resource.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "diagnostics.resource.network", BuggyToolCategory.DIAGNOSTICS, BuggySecurityCapabilities.DIAGNOSTICS_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("host", "service", "container"),
                "Read network telemetry for the authorized resource.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "diagnostics.dependency.check", BuggyToolCategory.DIAGNOSTICS, BuggySecurityCapabilities.DIAGNOSTICS_EXECUTE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("dependency", "service"),
                "Run a bounded dependency health probe.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "diagnostics.minecraft.server", BuggyToolCategory.DIAGNOSTICS, BuggySecurityCapabilities.DIAGNOSTICS_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("minecraft-server"),
                "Read Minecraft server diagnostics.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "diagnostics.velocity.proxy", BuggyToolCategory.DIAGNOSTICS, BuggySecurityCapabilities.DIAGNOSTICS_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("velocity-proxy"),
                "Read Velocity proxy diagnostics.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "diagnostics.postgres.health", BuggyToolCategory.DIAGNOSTICS, BuggySecurityCapabilities.DIAGNOSTICS_EXECUTE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("postgres-cluster"),
                "Run bounded PostgreSQL health diagnostics.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "diagnostics.container.inspect", BuggyToolCategory.DIAGNOSTICS, BuggySecurityCapabilities.DIAGNOSTICS_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("container"),
                "Inspect bounded container metadata and health.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "source.read", BuggyToolCategory.SOURCE, BuggySecurityCapabilities.SOURCE_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.PATH, BuggyToolSchemas.READ_OUTPUT,
                Set.of("source-repository"),
                "Read a bounded source file from an authorized source snapshot.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "source.search", BuggyToolCategory.SOURCE, BuggySecurityCapabilities.SOURCE_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.PATH_QUERY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("source-repository"),
                "Search source text inside an authorized source snapshot.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "source.diff", BuggyToolCategory.SOURCE, BuggySecurityCapabilities.SOURCE_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.REF_PAIR, BuggyToolSchemas.READ_OUTPUT,
                Set.of("source-repository"),
                "Compare two authorized immutable source references.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "source.history", BuggyToolCategory.SOURCE, BuggySecurityCapabilities.SOURCE_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.PATH, BuggyToolSchemas.READ_OUTPUT,
                Set.of("source-repository"),
                "Read bounded source history for a path.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "source.dependencies", BuggyToolCategory.SOURCE, BuggySecurityCapabilities.SOURCE_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.PATH, BuggyToolSchemas.READ_OUTPUT,
                Set.of("source-repository"),
                "Inspect declared source/build dependencies.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "source.analyze", BuggyToolCategory.SOURCE, BuggySecurityCapabilities.SOURCE_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.PATH, BuggyToolSchemas.READ_OUTPUT,
                Set.of("source-repository"),
                "Run bounded static source analysis without mutation.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "source.create-workspace", BuggyToolCategory.SOURCE, BuggySecurityCapabilities.SOURCE_WORKSPACE_MUTATE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.WORKSPACE_CREATE, BuggyToolSchemas.ARTIFACT_OUTPUT,
                Set.of("source-workspace"),
                "Create an isolated controlled source workspace.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "source.modify-workspace", BuggyToolCategory.SOURCE, BuggySecurityCapabilities.SOURCE_WORKSPACE_MUTATE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.WORKSPACE_MODIFY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("source-workspace"),
                "Modify only an authorized isolated source workspace.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "source.create-patch", BuggyToolCategory.SOURCE, BuggySecurityCapabilities.SOURCE_WORKSPACE_MUTATE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.PATCH_CREATE, BuggyToolSchemas.ARTIFACT_OUTPUT,
                Set.of("source-workspace"),
                "Create a traceable patch from an isolated workspace.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "source.compare-builds", BuggyToolCategory.SOURCE, BuggySecurityCapabilities.SOURCE_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.REF_PAIR, BuggyToolSchemas.READ_OUTPUT,
                Set.of("build-artifact"),
                "Compare build/source evidence without mutating production.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "source.static-analysis", BuggyToolCategory.SOURCE, BuggySecurityCapabilities.SOURCE_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.PATH, BuggyToolSchemas.READ_OUTPUT,
                Set.of("source-repository", "source-workspace"),
                "Run bounded static analysis over authorized source.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "build.gradle", BuggyToolCategory.BUILD, BuggySecurityCapabilities.BUILD_EXECUTE, ToolRiskLevel.LOW,
                BuggyToolSchemas.TARGETS, BuggyToolSchemas.ARTIFACT_OUTPUT,
                Set.of("build-workspace", "build-artifact"),
                "Run allow-listed Gradle build targets in a bounded build environment.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(120), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "build.maven", BuggyToolCategory.BUILD, BuggySecurityCapabilities.BUILD_EXECUTE, ToolRiskLevel.LOW,
                BuggyToolSchemas.TARGETS, BuggyToolSchemas.ARTIFACT_OUTPUT,
                Set.of("build-workspace", "build-artifact"),
                "Run allow-listed Maven goals in a bounded build environment.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(120), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "build.java", BuggyToolCategory.BUILD, BuggySecurityCapabilities.BUILD_EXECUTE, ToolRiskLevel.LOW,
                BuggyToolSchemas.TARGETS, BuggyToolSchemas.ARTIFACT_OUTPUT,
                Set.of("build-workspace", "build-artifact"),
                "Compile Java sources in a bounded build environment.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(120), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "build.test", BuggyToolCategory.BUILD, BuggySecurityCapabilities.BUILD_EXECUTE, ToolRiskLevel.LOW,
                BuggyToolSchemas.TEST, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("build-workspace", "build-artifact"),
                "Run bounded automated tests.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(120), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "build.integration-test", BuggyToolCategory.BUILD, BuggySecurityCapabilities.BUILD_EXECUTE, ToolRiskLevel.LOW,
                BuggyToolSchemas.TEST, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("build-workspace", "build-artifact"),
                "Run bounded integration tests in an isolated environment.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(120), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "build.security-test", BuggyToolCategory.BUILD, BuggySecurityCapabilities.BUILD_EXECUTE, ToolRiskLevel.LOW,
                BuggyToolSchemas.TEST, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("build-workspace", "build-artifact"),
                "Run bounded security verification.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(120), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "build.benchmark", BuggyToolCategory.BUILD, BuggySecurityCapabilities.BUILD_EXECUTE, ToolRiskLevel.LOW,
                BuggyToolSchemas.BENCHMARK, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("build-workspace", "build-artifact"),
                "Run bounded benchmarks with explicit iteration limits.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(120), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "build.jdeps", BuggyToolCategory.BUILD, BuggySecurityCapabilities.BUILD_EXECUTE, ToolRiskLevel.LOW,
                BuggyToolSchemas.TARGETS, BuggyToolSchemas.READ_OUTPUT,
                Set.of("build-workspace", "build-artifact"),
                "Inspect Java dependency relationships.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(120), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "build.package", BuggyToolCategory.BUILD, BuggySecurityCapabilities.BUILD_EXECUTE, ToolRiskLevel.LOW,
                BuggyToolSchemas.TARGETS, BuggyToolSchemas.ARTIFACT_OUTPUT,
                Set.of("build-workspace", "build-artifact"),
                "Create a traceable build artifact.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(120), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "build.verify-artifact", BuggyToolCategory.BUILD, BuggySecurityCapabilities.BUILD_EXECUTE, ToolRiskLevel.LOW,
                BuggyToolSchemas.ARTIFACT, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("build-workspace", "build-artifact"),
                "Verify an artifact against declared build metadata.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(120), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "build.sha256", BuggyToolCategory.BUILD, BuggySecurityCapabilities.BUILD_EXECUTE, ToolRiskLevel.LOW,
                BuggyToolSchemas.ARTIFACT, BuggyToolSchemas.READ_OUTPUT,
                Set.of("build-workspace", "build-artifact"),
                "Calculate and report an artifact SHA-256 digest.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(120), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "deployment.inspect", BuggyToolCategory.DEPLOYMENT, BuggySecurityCapabilities.DEPLOYMENT_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("deployment"),
                "Inspect deployment state and immutable metadata.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "deployment.plan", BuggyToolCategory.DEPLOYMENT, BuggySecurityCapabilities.DEPLOYMENT_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.DEPLOY_PLAN, BuggyToolSchemas.ARTIFACT_OUTPUT,
                Set.of("deployment-target"),
                "Create a deployment plan without applying it.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "deployment.stage", BuggyToolCategory.DEPLOYMENT, BuggySecurityCapabilities.DEPLOYMENT_NONPROD_EXECUTE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.DEPLOY_PLAN, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("deployment-nonprod"),
                "Deploy only to an authorized non-production target.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "deployment.verify", BuggyToolCategory.DEPLOYMENT, BuggySecurityCapabilities.DEPLOYMENT_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("deployment"),
                "Verify deployment health and artifact identity.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "deployment.rollback", BuggyToolCategory.DEPLOYMENT, BuggySecurityCapabilities.DEPLOYMENT_PRODUCTION_REQUEST, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.REASON, BuggyToolSchemas.REQUEST_OUTPUT,
                Set.of("deployment"),
                "Prepare a rollback request; direct production rollback is not exposed.", BuggyToolApprovalPolicy.REQUEST_ONLY,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "deployment.production", BuggyToolCategory.DEPLOYMENT, BuggySecurityCapabilities.DEPLOYMENT_PRODUCTION_REQUEST, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.DEPLOY_PLAN, BuggyToolSchemas.REQUEST_OUTPUT,
                Set.of("deployment-production"),
                "Create a production deployment request only.", BuggyToolApprovalPolicy.REQUEST_ONLY,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "deployment.status", BuggyToolCategory.DEPLOYMENT, BuggySecurityCapabilities.DEPLOYMENT_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("deployment"),
                "Read current deployment status.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "deployment.compare", BuggyToolCategory.DEPLOYMENT, BuggySecurityCapabilities.DEPLOYMENT_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.REF_PAIR, BuggyToolSchemas.READ_OUTPUT,
                Set.of("deployment"),
                "Compare two deployment revisions.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "infrastructure.host.status", BuggyToolCategory.INFRASTRUCTURE, BuggySecurityCapabilities.INFRASTRUCTURE_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("host"),
                "Read host status.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "infrastructure.container.status", BuggyToolCategory.INFRASTRUCTURE, BuggySecurityCapabilities.INFRASTRUCTURE_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("container"),
                "Read container status.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "infrastructure.container.restart", BuggyToolCategory.INFRASTRUCTURE, BuggySecurityCapabilities.INFRASTRUCTURE_RECOVERY_LOW, ToolRiskLevel.LOW,
                BuggyToolSchemas.REASON, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("container"),
                "Restart only an authorized low-risk recoverable container.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "infrastructure.service.restart", BuggyToolCategory.INFRASTRUCTURE, BuggySecurityCapabilities.INFRASTRUCTURE_RECOVERY_LOW, ToolRiskLevel.LOW,
                BuggyToolSchemas.REASON, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("service"),
                "Restart only an authorized low-risk recoverable service.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "infrastructure.disk.inspect", BuggyToolCategory.INFRASTRUCTURE, BuggySecurityCapabilities.INFRASTRUCTURE_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("host"),
                "Inspect bounded disk state.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "infrastructure.network.inspect", BuggyToolCategory.INFRASTRUCTURE, BuggySecurityCapabilities.INFRASTRUCTURE_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("host"),
                "Inspect host networking without mutation.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "infrastructure.resource.inspect", BuggyToolCategory.INFRASTRUCTURE, BuggySecurityCapabilities.INFRASTRUCTURE_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("host", "service", "container"),
                "Inspect bounded infrastructure resource metadata.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "infrastructure.config.inspect", BuggyToolCategory.INFRASTRUCTURE, BuggySecurityCapabilities.INFRASTRUCTURE_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("service", "container", "host"),
                "Inspect non-secret infrastructure configuration metadata.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "database.health", BuggyToolCategory.DATABASE, BuggySecurityCapabilities.DATABASE_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("database"),
                "Read database health and replication-safe metadata.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(60), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "database.schema.inspect", BuggyToolCategory.DATABASE, BuggySecurityCapabilities.DATABASE_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("database"),
                "Inspect authorized schema metadata.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(60), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "database.query.readonly", BuggyToolCategory.DATABASE, BuggySecurityCapabilities.DATABASE_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.READONLY_SQL, BuggyToolSchemas.READ_OUTPUT,
                Set.of("database"),
                "Execute a parser-enforced single read-only query with bounded rows.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(60), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "database.explain", BuggyToolCategory.DATABASE, BuggySecurityCapabilities.DATABASE_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.READONLY_SQL, BuggyToolSchemas.READ_OUTPUT,
                Set.of("database"),
                "Explain a parser-enforced read-only query without mutation.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(60), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "database.performance", BuggyToolCategory.DATABASE, BuggySecurityCapabilities.DATABASE_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("database"),
                "Read bounded database performance telemetry.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(60), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "database.connections", BuggyToolCategory.DATABASE, BuggySecurityCapabilities.DATABASE_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("database"),
                "Inspect bounded connection metadata.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(60), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "database.locks", BuggyToolCategory.DATABASE, BuggySecurityCapabilities.DATABASE_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("database"),
                "Inspect lock metadata without mutation.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(60), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "database.migration.inspect", BuggyToolCategory.DATABASE, BuggySecurityCapabilities.DATABASE_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("database"),
                "Inspect migration state and checksums.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(60), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "database.backup", BuggyToolCategory.DATABASE, BuggySecurityCapabilities.DATABASE_WRITE_REQUEST, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.REASON, BuggyToolSchemas.REQUEST_OUTPUT,
                Set.of("database"),
                "Create a typed database backup request; no direct write SQL is exposed.", BuggyToolApprovalPolicy.REQUEST_ONLY,
                DataClassification.RESTRICTED, Duration.ofSeconds(60), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "database.verify-backup", BuggyToolCategory.DATABASE, BuggySecurityCapabilities.DATABASE_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.ARTIFACT, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("database"),
                "Verify backup metadata and integrity.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(60), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "database.restore-plan", BuggyToolCategory.DATABASE, BuggySecurityCapabilities.DATABASE_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.ARTIFACT, BuggyToolSchemas.ARTIFACT_OUTPUT,
                Set.of("database"),
                "Prepare a restore plan without executing restoration.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(60), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "network.dns.inspect", BuggyToolCategory.NETWORK, BuggySecurityCapabilities.NETWORK_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.HOST, BuggyToolSchemas.READ_OUTPUT,
                Set.of("dns-zone"),
                "Inspect DNS records and resolution metadata.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "network.port.check", BuggyToolCategory.NETWORK, BuggySecurityCapabilities.NETWORK_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.HOST_PORT, BuggyToolSchemas.READ_OUTPUT,
                Set.of("network-endpoint"),
                "Probe one authorized host/port pair.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "network.latency", BuggyToolCategory.NETWORK, BuggySecurityCapabilities.NETWORK_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.HOST, BuggyToolSchemas.READ_OUTPUT,
                Set.of("network-endpoint"),
                "Measure bounded latency to an authorized endpoint.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "network.route.inspect", BuggyToolCategory.NETWORK, BuggySecurityCapabilities.NETWORK_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.HOST, BuggyToolSchemas.READ_OUTPUT,
                Set.of("network-endpoint"),
                "Inspect bounded route information without mutation.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "network.tls.inspect", BuggyToolCategory.NETWORK, BuggySecurityCapabilities.NETWORK_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.HOST_PORT, BuggyToolSchemas.READ_OUTPUT,
                Set.of("network-endpoint"),
                "Inspect TLS certificate and protocol metadata.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "network.service-connectivity", BuggyToolCategory.NETWORK, BuggySecurityCapabilities.NETWORK_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("service"),
                "Check service connectivity using an authorized service resource.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "network.firewall.inspect", BuggyToolCategory.NETWORK, BuggySecurityCapabilities.NETWORK_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("firewall"),
                "Inspect firewall rules without mutation.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "network.proxy.inspect", BuggyToolCategory.NETWORK, BuggySecurityCapabilities.NETWORK_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("proxy"),
                "Inspect proxy configuration without mutation.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(30), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "security.audit.search", BuggyToolCategory.SECURITY, BuggySecurityCapabilities.SECURITY_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.QUERY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("audit"),
                "Search security audit records.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "security.permission.inspect", BuggyToolCategory.SECURITY, BuggySecurityCapabilities.SECURITY_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("security-subject"),
                "Inspect effective permission metadata.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "security.actor.inspect", BuggyToolCategory.SECURITY, BuggySecurityCapabilities.SECURITY_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("actor"),
                "Inspect authoritative actor security metadata.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "security.capability.inspect", BuggyToolCategory.SECURITY, BuggySecurityCapabilities.SECURITY_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("assistant"),
                "Inspect assistant capability bindings.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "security.session.inspect", BuggyToolCategory.SECURITY, BuggySecurityCapabilities.SECURITY_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("security-session"),
                "Inspect session metadata without exposing secret material.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "security.token.metadata", BuggyToolCategory.SECURITY, BuggySecurityCapabilities.SECURITY_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("token-metadata"),
                "Inspect non-secret token metadata only.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "security.policy.inspect", BuggyToolCategory.SECURITY, BuggySecurityCapabilities.SECURITY_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("security-policy"),
                "Inspect effective security policy.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "security.incident.inspect", BuggyToolCategory.SECURITY, BuggySecurityCapabilities.SECURITY_READ, ToolRiskLevel.HIGH,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("security-incident"),
                "Inspect security incident evidence.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.CRITICAL_REQUIRED));
        tools.add(tool(
                "knowledge.search", BuggyToolCategory.KNOWLEDGE, BuggySecurityCapabilities.KNOWLEDGE_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.QUERY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("knowledge-catalog"),
                "Search Foundation Knowledge through Buggy B4 policy.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "knowledge.read", BuggyToolCategory.KNOWLEDGE, BuggySecurityCapabilities.KNOWLEDGE_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("knowledge-document"),
                "Read one authorized Foundation Knowledge document.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "knowledge.create-draft", BuggyToolCategory.KNOWLEDGE, BuggySecurityCapabilities.KNOWLEDGE_DRAFT_MUTATE, ToolRiskLevel.LOW,
                BuggyToolSchemas.KNOWLEDGE_DRAFT, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("knowledge-catalog"),
                "Create a Foundation Knowledge draft.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "knowledge.revise", BuggyToolCategory.KNOWLEDGE, BuggySecurityCapabilities.KNOWLEDGE_DRAFT_MUTATE, ToolRiskLevel.LOW,
                BuggyToolSchemas.REVISION, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("knowledge-document"),
                "Create a new draft revision without rewriting history.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "knowledge.detect-conflicts", BuggyToolCategory.KNOWLEDGE, BuggySecurityCapabilities.KNOWLEDGE_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.QUERY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("knowledge-catalog"),
                "Detect evidence-backed Knowledge conflicts.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "knowledge.mark-stale", BuggyToolCategory.KNOWLEDGE, BuggySecurityCapabilities.KNOWLEDGE_DRAFT_MUTATE, ToolRiskLevel.LOW,
                BuggyToolSchemas.MARK, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("knowledge-document"),
                "Append a Buggy stale/disputed assessment.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "knowledge.attach-source", BuggyToolCategory.KNOWLEDGE, BuggySecurityCapabilities.KNOWLEDGE_DRAFT_MUTATE, ToolRiskLevel.LOW,
                BuggyToolSchemas.RELATE, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("knowledge-document"),
                "Attach a typed provenance/source reference to a draft.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "knowledge.request-publish", BuggyToolCategory.KNOWLEDGE, BuggySecurityCapabilities.REQUESTS_CREATE, ToolRiskLevel.LOW,
                BuggyToolSchemas.REQUEST_CREATE, BuggyToolSchemas.REQUEST_OUTPUT,
                Set.of("knowledge-document"),
                "Create a publication request through Foundation Requests.", BuggyToolApprovalPolicy.REQUEST_ONLY,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "bugs.search", BuggyToolCategory.BUGS, BuggySecurityCapabilities.BUGS_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.QUERY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("bug"),
                "Search bug records.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(20), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "bugs.inspect", BuggyToolCategory.BUGS, BuggySecurityCapabilities.BUGS_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("bug"),
                "Inspect one bug record.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(20), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "bugs.create", BuggyToolCategory.BUGS, BuggySecurityCapabilities.BUGS_MUTATE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.BUG_CREATE, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("bug"),
                "Create a bug with evidence references.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(20), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "bugs.update", BuggyToolCategory.BUGS, BuggySecurityCapabilities.BUGS_MUTATE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.BUG_UPDATE, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("bug"),
                "Append a bounded bug update.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(20), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "bugs.relate", BuggyToolCategory.BUGS, BuggySecurityCapabilities.BUGS_MUTATE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.RELATE, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("bug"),
                "Relate two bug records.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(20), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "bugs.duplicate", BuggyToolCategory.BUGS, BuggySecurityCapabilities.BUGS_MUTATE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.RELATE, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("bug"),
                "Mark a bug as duplicate of another record.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(20), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "bugs.regression", BuggyToolCategory.BUGS, BuggySecurityCapabilities.BUGS_MUTATE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.RELATE, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("bug"),
                "Link a regression to prior bug evidence.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(20), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "bugs.attach-build", BuggyToolCategory.BUGS, BuggySecurityCapabilities.BUGS_MUTATE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.RELATE, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("bug"),
                "Attach a build reference to a bug.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(20), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "bugs.attach-incident", BuggyToolCategory.BUGS, BuggySecurityCapabilities.BUGS_MUTATE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.RELATE, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("bug"),
                "Attach an incident reference to a bug.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(20), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "bugs.triage", BuggyToolCategory.BUGS, BuggySecurityCapabilities.BUGS_MUTATE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.BUG_UPDATE, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("bug"),
                "Append triage classification/evidence.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(20), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "bugs.resolve", BuggyToolCategory.BUGS, BuggySecurityCapabilities.BUGS_MUTATE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.BUG_UPDATE, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("bug"),
                "Resolve a bug with an auditable note.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(20), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "bugs.reopen", BuggyToolCategory.BUGS, BuggySecurityCapabilities.BUGS_MUTATE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.BUG_UPDATE, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("bug"),
                "Reopen a bug with evidence.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(20), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "requests.create", BuggyToolCategory.REQUESTS, BuggySecurityCapabilities.REQUESTS_CREATE, ToolRiskLevel.LOW,
                BuggyToolSchemas.REQUEST_CREATE, BuggyToolSchemas.REQUEST_OUTPUT,
                Set.of("request"),
                "Create a typed Foundation request.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "requests.inspect", BuggyToolCategory.REQUESTS, BuggySecurityCapabilities.REQUESTS_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.REQUEST_ID, BuggyToolSchemas.READ_OUTPUT,
                Set.of("request"),
                "Inspect request state and approval history.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "requests.update-payload", BuggyToolCategory.REQUESTS, BuggySecurityCapabilities.REQUESTS_CREATE, ToolRiskLevel.LOW,
                BuggyToolSchemas.REQUEST_UPDATE, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("request"),
                "Create a new request payload revision; never rewrite approved history.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "requests.cancel", BuggyToolCategory.REQUESTS, BuggySecurityCapabilities.REQUESTS_CREATE, ToolRiskLevel.LOW,
                BuggyToolSchemas.REQUEST_ID, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("request"),
                "Cancel a request according to Foundation state rules.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "requests.observe-status", BuggyToolCategory.REQUESTS, BuggySecurityCapabilities.REQUESTS_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.REQUEST_ID, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("request"),
                "Observe request status without approving it.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "assistant.status", BuggyToolCategory.SUPERVISOR, BuggySecurityCapabilities.SUPERVISOR_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("assistant"),
                "Read assistant runtime status.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "assistant.health", BuggyToolCategory.SUPERVISOR, BuggySecurityCapabilities.SUPERVISOR_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("assistant"),
                "Read assistant health.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "assistant.metrics", BuggyToolCategory.SUPERVISOR, BuggySecurityCapabilities.SUPERVISOR_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("assistant"),
                "Read bounded assistant metrics.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "assistant.failures", BuggyToolCategory.SUPERVISOR, BuggySecurityCapabilities.SUPERVISOR_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("assistant"),
                "Inspect assistant failures.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "assistant.degrade", BuggyToolCategory.SUPERVISOR, BuggySecurityCapabilities.SUPERVISOR_EXECUTE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.REASON, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("assistant"),
                "Apply an authorized bounded degradation action.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "assistant.restart", BuggyToolCategory.SUPERVISOR, BuggySecurityCapabilities.SUPERVISOR_EXECUTE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.REASON, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("assistant"),
                "Restart an authorized assistant runtime without changing authority.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "assistant.pause", BuggyToolCategory.SUPERVISOR, BuggySecurityCapabilities.SUPERVISOR_EXECUTE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.REASON, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("assistant"),
                "Pause an authorized assistant runtime.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "assistant.resume", BuggyToolCategory.SUPERVISOR, BuggySecurityCapabilities.SUPERVISOR_EXECUTE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.REASON, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("assistant"),
                "Resume an authorized assistant runtime.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "assistant.inspect-configuration", BuggyToolCategory.SUPERVISOR, BuggySecurityCapabilities.SUPERVISOR_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("assistant"),
                "Inspect non-secret assistant configuration.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "assistant.inspect-tools", BuggyToolCategory.SUPERVISOR, BuggySecurityCapabilities.SUPERVISOR_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("assistant"),
                "Inspect registered assistant tool metadata.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "assistant.inspect-knowledge", BuggyToolCategory.SUPERVISOR, BuggySecurityCapabilities.SUPERVISOR_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("assistant"),
                "Inspect assistant Knowledge bindings.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "minecraft.server.status", BuggyToolCategory.MINECRAFT, BuggySecurityCapabilities.MINECRAFT_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("minecraft-server"),
                "Read Minecraft server status.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "minecraft.server.health", BuggyToolCategory.MINECRAFT, BuggySecurityCapabilities.MINECRAFT_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.STATUS_OUTPUT,
                Set.of("minecraft-server"),
                "Read Minecraft health telemetry.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "minecraft.player.inspect", BuggyToolCategory.MINECRAFT, BuggySecurityCapabilities.MINECRAFT_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("minecraft-player"),
                "Inspect bounded player metadata.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "minecraft.world.inspect", BuggyToolCategory.MINECRAFT, BuggySecurityCapabilities.MINECRAFT_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("minecraft-world"),
                "Inspect world metadata and health.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "minecraft.plugin.inspect", BuggyToolCategory.MINECRAFT, BuggySecurityCapabilities.MINECRAFT_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("minecraft-server"),
                "Inspect loaded plugin metadata.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "minecraft.whitelist.inspect", BuggyToolCategory.MINECRAFT, BuggySecurityCapabilities.MINECRAFT_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("minecraft-server"),
                "Inspect whitelist metadata.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "minecraft.whitelist.add", BuggyToolCategory.MINECRAFT, BuggySecurityCapabilities.MINECRAFT_EXECUTE_LOW, ToolRiskLevel.LOW,
                BuggyToolSchemas.WHITELIST, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("minecraft-server"),
                "Add one player to an authorized whitelist; no generic command pass-through.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "minecraft.whitelist.remove", BuggyToolCategory.MINECRAFT, BuggySecurityCapabilities.MINECRAFT_EXECUTE_LOW, ToolRiskLevel.LOW,
                BuggyToolSchemas.WHITELIST, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("minecraft-server"),
                "Remove one player from an authorized whitelist; no generic command pass-through.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "discord.guild.inspect", BuggyToolCategory.DISCORD, BuggySecurityCapabilities.DISCORD_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("discord-guild"),
                "Inspect guild metadata.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "discord.member.inspect", BuggyToolCategory.DISCORD, BuggySecurityCapabilities.DISCORD_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("discord-member"),
                "Inspect member metadata.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "discord.channel.inspect", BuggyToolCategory.DISCORD, BuggySecurityCapabilities.DISCORD_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("discord-channel"),
                "Inspect channel metadata.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "discord.role.inspect", BuggyToolCategory.DISCORD, BuggySecurityCapabilities.DISCORD_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("discord-role"),
                "Inspect role metadata without mutation.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "discord.message.create", BuggyToolCategory.DISCORD, BuggySecurityCapabilities.DISCORD_EXECUTE_LOW, ToolRiskLevel.LOW,
                BuggyToolSchemas.MESSAGE, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("discord-channel"),
                "Create one bounded Discord message.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "discord.message.edit-own", BuggyToolCategory.DISCORD, BuggySecurityCapabilities.DISCORD_EXECUTE_LOW, ToolRiskLevel.LOW,
                BuggyToolSchemas.MESSAGE_EDIT, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("discord-channel"),
                "Edit only a Buggy-authored message through a typed adapter.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "discord.message.delete-own", BuggyToolCategory.DISCORD, BuggySecurityCapabilities.DISCORD_EXECUTE_LOW, ToolRiskLevel.LOW,
                BuggyToolSchemas.MESSAGE_ID, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("discord-channel"),
                "Delete only a Buggy-authored message through a typed adapter.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.CONFIDENTIAL, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "web.http.get", BuggyToolCategory.WEB, BuggySecurityCapabilities.WEB_EXECUTE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.URL, BuggyToolSchemas.READ_OUTPUT,
                Set.of("web-endpoint"),
                "Perform a bounded HTTP GET subject to canonical SSRF/redirect/content limits.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.INTERNAL, Duration.ofSeconds(30), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "web.http.head", BuggyToolCategory.WEB, BuggySecurityCapabilities.WEB_EXECUTE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.URL, BuggyToolSchemas.READ_OUTPUT,
                Set.of("web-endpoint"),
                "Perform a bounded HTTP HEAD subject to canonical SSRF/redirect limits.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.INTERNAL, Duration.ofSeconds(30), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "web.page.inspect", BuggyToolCategory.WEB, BuggySecurityCapabilities.WEB_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.URL, BuggyToolSchemas.READ_OUTPUT,
                Set.of("web-endpoint"),
                "Inspect normalized page content supplied by the bounded Web adapter.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.INTERNAL, Duration.ofSeconds(30), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "web.link.inspect", BuggyToolCategory.WEB, BuggySecurityCapabilities.WEB_READ, ToolRiskLevel.LOW,
                BuggyToolSchemas.URL, BuggyToolSchemas.READ_OUTPUT,
                Set.of("web-endpoint"),
                "Inspect one link target using bounded Web policy metadata.", BuggyToolApprovalPolicy.AUTONOMOUS_LOW,
                DataClassification.INTERNAL, Duration.ofSeconds(30), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "mail.search", BuggyToolCategory.MAIL, BuggySecurityCapabilities.MAIL_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.QUERY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("mailbox"),
                "Search authorized mail metadata/content.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "mail.read", BuggyToolCategory.MAIL, BuggySecurityCapabilities.MAIL_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("mail-message"),
                "Read one authorized mail message.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "mail.draft.prepare", BuggyToolCategory.MAIL, BuggySecurityCapabilities.MAIL_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.MAIL_DRAFT, BuggyToolSchemas.ARTIFACT_OUTPUT,
                Set.of("mailbox"),
                "Prepare a local draft artifact without sending or mutating the mailbox.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "mail.send.request", BuggyToolCategory.MAIL, BuggySecurityCapabilities.MAIL_SEND_REQUEST, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.MAIL_SEND_REQUEST, BuggyToolSchemas.REQUEST_OUTPUT,
                Set.of("mail-draft"),
                "Create a send request; direct mail sending is not exposed.", BuggyToolApprovalPolicy.REQUEST_ONLY,
                DataClassification.RESTRICTED, Duration.ofSeconds(20), 4, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "panel.overview.read", BuggyToolCategory.PANEL, BuggySecurityCapabilities.PANEL_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.EMPTY, BuggyToolSchemas.READ_OUTPUT,
                Set.of("panel-overview"),
                "Read Buggy Control Center overview data.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "panel.approvals.read", BuggyToolCategory.PANEL, BuggySecurityCapabilities.PANEL_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("panel-approvals"),
                "Read approval surfaces without approving anything.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "panel.incidents.read", BuggyToolCategory.PANEL, BuggySecurityCapabilities.PANEL_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("panel-incidents"),
                "Read incident surfaces.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "panel.knowledge.read", BuggyToolCategory.PANEL, BuggySecurityCapabilities.PANEL_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("panel-knowledge"),
                "Read Knowledge surfaces.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "panel.tools.read", BuggyToolCategory.PANEL, BuggySecurityCapabilities.PANEL_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("panel-tools"),
                "Read tool catalog/runtime surfaces.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "panel.security.read", BuggyToolCategory.PANEL, BuggySecurityCapabilities.PANEL_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("panel-security"),
                "Read security status without changing authority.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "panel.audit.read", BuggyToolCategory.PANEL, BuggySecurityCapabilities.PANEL_READ, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.FILTER, BuggyToolSchemas.READ_OUTPUT,
                Set.of("panel-audit"),
                "Read bounded audit surfaces.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        tools.add(tool(
                "panel.notification.acknowledge", BuggyToolCategory.PANEL, BuggySecurityCapabilities.PANEL_EXECUTE, ToolRiskLevel.MEDIUM,
                BuggyToolSchemas.REASON, BuggyToolSchemas.ACTION_OUTPUT,
                Set.of("panel-notification"),
                "Acknowledge one authorized Control Center notification.", BuggyToolApprovalPolicy.FOUNDATION_POLICY_GATED,
                DataClassification.RESTRICTED, Duration.ofSeconds(15), 8, ToolAuditPolicy.REQUIRED));
        return List.copyOf(tools);
    }

    private static BuggyToolDefinition tool(
            String id,
            BuggyToolCategory category,
            AssistantCapabilityId capability,
            ToolRiskLevel risk,
            ToolSchema input,
            ToolSchema output,
            Set<String> resources,
            String description,
            BuggyToolApprovalPolicy approvalPolicy,
            DataClassification classification,
            Duration timeout,
            int maxConcurrency,
            ToolAuditPolicy auditPolicy) {
        ToolDescriptor descriptor = new ToolDescriptor(
                new ToolId("tool." + id),
                capability,
                risk,
                input,
                output,
                timeout,
                maxConcurrency,
                auditPolicy,
                new ToolClassificationPolicy(classification, classification),
                false);
        return new BuggyToolDefinition(
                descriptor,
                category,
                description,
                resources,
                approvalPolicy,
                BuggyToolSecretHandling.REJECT_SECRET_INPUT_AND_REDACT_OUTPUT,
                approvalPolicy == BuggyToolApprovalPolicy.OWNER_ONLY);
    }

    private static Map<ToolId, BuggyToolDefinition> index(List<BuggyToolDefinition> definitions) {
        LinkedHashMap<ToolId, BuggyToolDefinition> indexed = new LinkedHashMap<>();
        for (BuggyToolDefinition definition : definitions) {
            if (indexed.put(definition.descriptor().toolId(), definition) != null) {
                throw new IllegalStateException("duplicate Buggy tool ID: " + definition.descriptor().toolId());
            }
        }
        return Map.copyOf(indexed);
    }
}
