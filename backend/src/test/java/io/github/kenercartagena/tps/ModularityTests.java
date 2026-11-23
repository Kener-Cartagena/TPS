package io.github.kenercartagena.tps;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Architecture tests for the modular monolith (ADR 0001).
 *
 * <p>Spring Modulith treats every direct sub-package of {@code io.github.kenercartagena.tps}
 * as an application module. These tests fail the build if a module depends on another
 * module's internal types or if module dependencies form a cycle.
 */
class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(TpsApplication.class);

    @Test
    void moduleBoundariesAreRespected() {
        MODULES.verify();
    }

    /** Writes module diagrams (PlantUML) and module canvases to target/spring-modulith-docs. */
    @Test
    void writeModuleDocumentation() {
        new Documenter(MODULES)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}
