/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.gateway.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit-based plane separation guard ensuring the gateway module has zero
 * data-plane dependencies (Invariant T4, ADR-0081 §8 Phase 2).
 *
 * <p>Validates at the bytecode level — catches transitive dependencies,
 * POM properties, and shaded packages that string-grep approaches miss.
 */
@DisplayName("Gateway Plane Separation Guard Test (Invariant T4, ADR-0081 §8 Phase 2)")
class GatewayPlaneSeparationGuardTest {

    private static JavaClasses gatewayClasses;

    @BeforeAll
    static void importClasses() {
        gatewayClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.spectrayan.spector.gateway");
    }

    @Test
    @DisplayName("T4: Gateway must not depend on memory/kernel/storage/indexing packages")
    void gatewayMustNotDependOnDataPlane() {
        noClasses()
                .that().resideInAPackage("com.spectrayan.spector.gateway..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.spectrayan.spector.memory..",
                        "com.spectrayan.spector.kernel..",
                        "com.spectrayan.spector.gpu..",
                        "com.spectrayan.spector.index..",
                        "com.spectrayan.spector.connector..",
                        "com.spectrayan.spector.batch..",
                        "com.spectrayan.spector.ingestion..",
                        "com.spectrayan.spector.mcp.."
                )
                .as("Gateway classes must not depend on data-plane packages " +
                    "(memory, kernel, gpu, index, connector, batch, ingestion, mcp)")
                .check(gatewayClasses);
    }

    @Test
    @DisplayName("T4: Gateway must not depend on servlet/Tomcat (reactive only)")
    void gatewayMustNotDependOnServletStack() {
        noClasses()
                .that().resideInAPackage("com.spectrayan.spector.gateway..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.apache.catalina..",
                        "org.apache.tomcat..",
                        "jakarta.servlet.."
                )
                .as("Gateway is a reactive (WebFlux) module — must not depend on servlet/Tomcat")
                .check(gatewayClasses);
    }

    @Test
    @DisplayName("T4: Gateway must not depend on data infrastructure (Flyway, H2, Camel, Quartz)")
    void gatewayMustNotDependOnDataInfrastructure() {
        noClasses()
                .that().resideInAPackage("com.spectrayan.spector.gateway..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.flywaydb..",
                        "org.h2..",
                        "org.apache.camel..",
                        "org.quartz.."
                )
                .as("Gateway must not depend on data infrastructure libraries")
                .check(gatewayClasses);
    }
}
