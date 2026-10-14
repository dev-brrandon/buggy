package de.legitserver.assistant.operations;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Static verification of the Phase-17 deploy/operations contracts. */
public final class Phase17DeploymentOperationsSelfTest {
    private Phase17DeploymentOperationsSelfTest() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("project root required");
        Path root = Path.of(args[0]);
        verifyDockerfile(root.resolve("Dockerfile"));
        verifyDeployment(root.resolve("deployment/kubernetes/base/deployment.yaml"));
        verifyNetworkPolicies(root.resolve("deployment/kubernetes/base"));
        verifyBackupScripts(root.resolve("deployment/postgres"));
        verifyRunbook(root.resolve("deployment/runbooks/PHASE17-OPERATIONS-RUNBOOK.md"));
        verifyRuntimeConfig(root.resolve("assistant-bootstrap/src/main/resources/application.yml"));
        System.out.println("PHASE17_DEPLOYMENT_OPERATIONS_SELF_TEST: PASS");
    }

    private static void verifyDockerfile(Path file) throws Exception {
        String value = Files.readString(file);
        require(value.contains("eclipse-temurin:25-jre-alpine"), "container must use minimal Java 25 runtime image");
        require(value.contains("USER 10001:10001"), "container must run as non-root UID/GID 10001");
        reject(value, "docker.sock", "Dockerfile must not mount/reference Docker socket");
        reject(value, "COPY .env", "Dockerfile must not copy dotenv secrets");
        reject(value, "COPY /", "Dockerfile must not copy host root");
    }

    private static void verifyDeployment(Path file) throws Exception {
        String value = Files.readString(file);
        for (String required : List.of(
                "runAsNonRoot: true",
                "runAsUser: 10001",
                "runAsGroup: 10001",
                "readOnlyRootFilesystem: true",
                "allowPrivilegeEscalation: false",
                "automountServiceAccountToken: false",
                "drop:",
                "- ALL",
                "seccompProfile:",
                "RuntimeDefault",
                "path: /health/live",
                "path: /health/ready")) {
            require(value.contains(required), "deployment missing hardening contract: " + required);
        }
        reject(value, "hostPath:", "hostPath volumes are forbidden");
        reject(value, "/var/run/docker.sock", "Docker socket is forbidden");
    }

    private static void verifyNetworkPolicies(Path dir) throws Exception {
        String deny = Files.readString(dir.resolve("networkpolicy-default-deny.yaml"));
        require(deny.contains("policyTypes:") && deny.contains("- Ingress") && deny.contains("- Egress"),
                "default deny must cover ingress and egress");
        require(deny.contains("ingress: []") && deny.contains("egress: []"), "default deny lists must be empty");

        String allow = Files.readString(dir.resolve("networkpolicy-allow-required.yaml"));
        require(allow.contains("kubernetes.io/metadata.name: legit-panel"), "Panel ingress allowlist missing");
        require(allow.contains("kubernetes.io/metadata.name: legit-database"), "PostgreSQL egress allowlist missing");
        require(allow.contains("kubernetes.io/metadata.name: legit-network"), "NetworkCore egress allowlist missing");
        require(allow.contains("port: 53"), "DNS egress allowlist missing");
    }

    private static void verifyBackupScripts(Path dir) throws Exception {
        String backup = Files.readString(dir.resolve("backup-postgres.sh"));
        String verify = Files.readString(dir.resolve("verify-backup.sh"));
        String restore = Files.readString(dir.resolve("restore-postgres.sh"));
        for (String schema : List.of("assistant", "bugs", "knowledge", "requests", "audit")) {
            require(backup.contains("--schema=" + schema), "backup missing schema: " + schema);
        }
        require(backup.contains("umask 077"), "backup files must default private");
        require(backup.contains("sha256sum"), "backup must produce checksum");
        require(verify.contains("pg_restore") && verify.contains("sha256sum -c"), "backup verifier incomplete");
        require(restore.contains("LEGIT_ASSISTANT_RESTORE_CONFIRM"), "restore must require explicit confirmation");
        require(restore.contains("--exit-on-error"), "restore must fail fast");
    }

    private static void verifyRunbook(Path file) throws Exception {
        String value = Files.readString(file);
        require(value.contains("RPO <= 15 minutes"), "RPO target missing");
        require(value.contains("RTO <= 60 minutes"), "RTO target missing");
        for (String marker : List.of("Backup", "Restore", "Rollback", "Graceful shutdown", "Dependency outage")) {
            require(value.contains(marker), "runbook section missing: " + marker);
        }
    }

    private static void verifyRuntimeConfig(Path file) throws Exception {
        String value = Files.readString(file);
        require(value.contains("shutdown: graceful"), "graceful Spring shutdown must remain enabled");
        require(value.contains("timeout-per-shutdown-phase: 20s"), "shutdown timeout must remain bounded");
        require(value.contains("LEGIT_ASSISTANT_MANAGEMENT_ADDRESS:127.0.0.1"), "management must remain loopback by default");
    }

    private static void reject(String value, String forbidden, String message) {
        require(!value.contains(forbidden), message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
