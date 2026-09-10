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
package com.spectrayan.spector.core.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces the architectural boundary rules established by ADR-0033 (Principles 1 &amp; 5).
 *
 * <p>Validates that {@code spector-core} remains a pure mathematical library with zero
 * domain model infiltration and no dependencies on upstream modules.</p>
 */
@DisplayName("ADR-0033 Principle 1 & 5: Spector Core Architectural Boundary Enforcement")
class CoreBoundaryRulesTest {

    private static final String CORE_PKG = "com.spectrayan.spector.core";
    private static final String COGNITIVE_PKG = "com.spectrayan.spector.core.cognitive..";
    private static JavaClasses coreClasses;

    @BeforeAll
    static void importCoreClasses() {
        coreClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(CORE_PKG);

        // Subject-count guard against issue #734 (ArchUnit importing 0 classes on class-file major 69)
        assertThat(coreClasses)
                .as("Core classes imported by ArchUnit must not be empty (guarding against issue #734)")
                .hasSizeGreaterThan(50);
    }

    @Test
    @DisplayName("spector-core must never depend on upstream storage, memory, config, index, or provider modules (Principle 1)")
    void coreMustNotDependOnUpstreamModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.spectrayan.spector.core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..spector.kernel..",
                        "..spector.memory..",
                        "..spector.index..",
                        "..spector.config..",
                        "..spector.events..",
                        "..spector.provider..",
                        "..spector.providers..",
                        "..spector.synapse..")
                .because("ADR-0033 Principle 1 mandates zero domain model infiltration into spector-core");

        rule.check(coreClasses);
    }

    @Test
    @DisplayName("spector-core cognitive kernels must be pure math free of java.lang.foreign (Principle 1)")
    void cognitiveKernelsMustNotDependOnForeign() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(COGNITIVE_PKG)
                .should().dependOnClassesThat().resideInAnyPackage("java.lang.foreign..")
                .because("cognitive kernels must operate solely on primitive scalars and arrays, free of off-heap handles");

        rule.check(coreClasses);
    }
}
