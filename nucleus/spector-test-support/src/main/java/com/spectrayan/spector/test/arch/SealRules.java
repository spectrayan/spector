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
package com.spectrayan.spector.test.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared architectural boundary rules enforcing memory kernel sealing across all Spector modules.
 *
 * <p>Centralizes boundary rules so that {@code spector-kernel}, {@code spector-memory},
 * {@code spector-inspect}, and {@code spector-mcp} assert the exact same contract rather than
 * maintaining divergent rule sets.</p>
 */
public final class SealRules {

    public static final String KERNEL = "com.spectrayan.spector.kernel..";

    /** Permitted callers of kernel.unsafe — data, not logic, so additions are a reviewable diff. */
    public static final List<String> UNSAFE_ALLOWLIST = List.of(
            "com.spectrayan.spector.inspect..",
            "com.spectrayan.spector.cli.."
    );

    public static final ArchRule NO_FOREIGN_OUTSIDE_KERNEL = noClasses()
            .that().resideOutsideOfPackage(KERNEL)
            .should().dependOnClassesThat().resideInAnyPackage("java.lang.foreign..")
            .because("Panama I/O is sealed inside spector-kernel (spec R3.5, R11.6)");

    public static final ArchRule NO_ARENA_OUTSIDE_KERNEL = noClasses()
            .that().resideOutsideOfPackage(KERNEL)
            .should().callMethod(java.lang.foreign.Arena.class, "ofShared")
            .orShould().callMethod(java.lang.foreign.Arena.class, "ofConfined")
            .orShould().callMethod(java.lang.foreign.Arena.class, "ofAuto")
            .orShould().callMethod(java.lang.foreign.Arena.class, "global")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.foreign.Arena")
            .because("no code outside the kernel shall construct or depend on an Arena (spec R8.2)");

    public static final ArchRule KERNEL_HAS_NO_COGNITIVE_TYPES = noClasses()
            .that().resideInAPackage(KERNEL)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.spectrayan.spector.memory..",
                    "com.spectrayan.spector.config..")
            .because("kernel must not depend on policy, config, or crypto (spec R5)");

    /**
     * Subject-count guard — guards against empty class imports (the #734 defect).
     * Must run before any rule assertion.
     *
     * @param pkg package prefix to import
     * @param minimumExpected minimum number of non-test classes expected in the package
     * @return imported JavaClasses
     */
    public static JavaClasses importedOrFail(String pkg, int minimumExpected) {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(pkg);
        assertThat(classes)
                .as("imported classes from %s — a zero import passes every noClasses() rule vacuously "
                        + "and is the exact failure recorded in #734", pkg)
                .hasSizeGreaterThan(minimumExpected);
        return classes;
    }

    private SealRules() {
    }
}
