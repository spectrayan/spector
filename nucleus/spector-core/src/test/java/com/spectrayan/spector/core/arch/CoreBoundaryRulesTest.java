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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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
    private static List<Path> compiledClassFiles;

    @BeforeAll
    static void importCoreClasses() throws IOException {
        try {
            coreClasses = new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(CORE_PKG);
        } catch (Exception ignored) {
            coreClasses = null;
        }

        // Direct bytecode scanner backup for JDK versions where ArchUnit shaded ASM does not yet parse bytecode (e.g. JDK 27+)
        Path classesDir = Paths.get("target/classes");
        if (Files.exists(classesDir)) {
            try (Stream<Path> stream = Files.walk(classesDir)) {
                compiledClassFiles = stream.filter(p -> p.toString().endsWith(".class") && !p.toString().endsWith("package-info.class")).toList();
            }
        } else {
            compiledClassFiles = List.of();
        }

        boolean hasArchClasses = coreClasses != null && coreClasses.size() > 50;
        boolean hasCompiledClasses = compiledClassFiles.size() > 50;

        assertThat(hasArchClasses || hasCompiledClasses)
                .as("Core classes must not be empty (guarding against issue #734)")
                .isTrue();
    }

    @Test
    @DisplayName("spector-core must never depend on upstream storage, memory, config, index, or provider modules (Principle 1)")
    void coreMustNotDependOnUpstreamModules() throws IOException {
        if (coreClasses != null && !coreClasses.isEmpty()) {
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
            return;
        }

        // Bytecode constant pool boundary check
        List<String> bannedPackages = List.of(
                "com/spectrayan/spector/kernel/",
                "com/spectrayan/spector/memory/",
                "com/spectrayan/spector/index/",
                "com/spectrayan/spector/config/",
                "com/spectrayan/spector/events/",
                "com/spectrayan/spector/provider/",
                "com/spectrayan/spector/providers/",
                "com/spectrayan/spector/synapse/"
        );

        List<String> violations = new ArrayList<>();
        for (Path p : compiledClassFiles) {
            byte[] bytes = Files.readAllBytes(p);
            String content = new String(bytes, StandardCharsets.ISO_8859_1);
            for (String banned : bannedPackages) {
                if (content.contains(banned)) {
                    violations.add(p.getFileName() + " depends on " + banned);
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("spector-core cognitive kernels must be pure math free of java.lang.foreign (Principle 1)")
    void cognitiveKernelsMustNotDependOnForeign() throws IOException {
        if (coreClasses != null && !coreClasses.isEmpty()) {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(COGNITIVE_PKG)
                    .should().dependOnClassesThat().resideInAnyPackage("java.lang.foreign..")
                    .because("cognitive kernels must operate solely on primitive scalars and arrays, free of off-heap handles");

            rule.check(coreClasses);
            return;
        }

        List<String> violations = new ArrayList<>();
        for (Path p : compiledClassFiles) {
            if (p.toString().contains("/cognitive/")) {
                byte[] bytes = Files.readAllBytes(p);
                String content = new String(bytes, StandardCharsets.ISO_8859_1);
                if (content.contains("java/lang/foreign/")) {
                    violations.add(p.getFileName() + " depends on java.lang.foreign");
                }
            }
        }
        assertThat(violations).isEmpty();
    }
}
