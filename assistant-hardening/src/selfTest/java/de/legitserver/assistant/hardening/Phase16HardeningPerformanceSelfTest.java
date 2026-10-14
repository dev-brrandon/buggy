package de.legitserver.assistant.hardening;

import java.nio.file.Path;

/** Phase-16 acceptance entry point. */
public final class Phase16HardeningPerformanceSelfTest {
    private Phase16HardeningPerformanceSelfTest() {}

    public static void main(String[] args) throws Exception {
        Path projectRoot = args.length == 0 ? Path.of(".").toAbsolutePath().normalize() : Path.of(args[0]).toAbsolutePath().normalize();
        SecurityAttackSuite.run(projectRoot);
        PerformanceSuite.run();
        HardeningArchitectureSuite.run(projectRoot);
        MemorySoakSuite.run();
        System.out.println("PHASE16_HARDENING_PERFORMANCE_SELF_TEST: PASS");
    }
}
