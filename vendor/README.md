# Vendored Phase-1 Spring runtime baseline

Phase 1 is intentionally verifiable without network dependency resolution.
The JARs in `panel-gateway-libs/` are the minimal Spring Boot Web/Actuator runtime subset extracted from the project-provided canonical baseline:

`legit-panel-gateway-0.1.0-dev.19.jar`

That canonical baseline runs Spring Boot 4.1.1. Database, Flyway, session and Spring Security runtime libraries are deliberately not copied into Phase 1 because those concerns belong to later foundation phases.

`SHA256SUMS` pins every extracted JAR. This vendor directory is a bootstrap/reproducibility mechanism, not a new dependency authority.
