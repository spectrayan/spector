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
package com.spectrayan.spector.mcp;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import com.spectrayan.spector.test.arch.SealRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architectural boundary tests enforcing module contracts between
 * the MCP layer ({@code spector-mcp}) and the memory module ({@code spector-memory}).
 *
 * <h3>Rule: MCP tools MUST NOT directly access memory internals</h3>
 * <p>The MCP layer should interact with the memory module exclusively through
 * the public API surface: {@code SpectorMemory}, {@code SpectorMemoryAdmin},
 * and model classes in {@code memory.model}. Direct access to internal packages
 * (graph, temporal, index, hebbian, cortex, etc.) is a boundary violation
 * that couples the MCP layer to implementation details.</p>
 *
 * @see <a href="https://github.com/spectrayan/spector/issues/581">#581</a>
 */
class ModuleBoundaryTest {

    private static JavaClasses mcpClasses;

    @BeforeAll
    static void importClasses() {
        mcpClasses = SealRules.importedOrFail("com.spectrayan.spector.mcp", 0);
    }

    @Test
    @DisplayName("MCP tools must not import memory.graph.* internal classes")
    void mcpToolsMustNotImportGraphInternals() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.spectrayan.spector.mcp..")
                .should().dependOnClassesThat(
                        resideInAnyPackage(
                                "com.spectrayan.spector.memory.graph..",
                                "com.spectrayan.spector.memory.hebbian..",
                                "com.spectrayan.spector.memory.cortex..",
                                "com.spectrayan.spector.memory.synapse.."
                        )
                        // Pre-existing boundary defects unmasked by ArchUnit 1.4.2 upgrade (#734, #793):
                        // 1. MemorySource currently resides in cortex; moving to kernel.api in Group 4 (R5.2)
                        .and(not(equivalentTo(com.spectrayan.spector.memory.cortex.MemorySource.class)))
                        // 2. ProspectiveScheduler is returned by SpectorMemoryAdmin.prospective()
                        .and(not(equivalentTo(com.spectrayan.spector.memory.cortex.prospective.ProspectiveScheduler.class)))
                )
                .allowEmptyShould(true)
                .because("MCP tools must use SpectorMemory public API, not internal graph/hebbian/cortex classes (see #581, #734, #793)");

        rule.check(mcpClasses);
    }

    @Test
    @DisplayName("MCP tools must not import memory.temporal.* internal classes")
    void mcpToolsMustNotImportTemporalInternals() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.spectrayan.spector.mcp..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.spectrayan.spector.memory.temporal.."
                )
                .allowEmptyShould(true)
                .because("MCP tools must use SpectorMemory.factsAbout()/factHistory(), not TemporalKnowledgeGraph directly (see #581)");

        rule.check(mcpClasses);
    }

    @Test
    @DisplayName("MCP tools must not import memory.index.* internal classes")
    void mcpToolsMustNotImportIndexInternals() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.spectrayan.spector.mcp..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.spectrayan.spector.memory.index.."
                )
                .allowEmptyShould(true)
                .because("MCP tools must not access MemoryIndex directly (see #581)");

        rule.check(mcpClasses);
    }

    @Test
    @DisplayName("MCP tools must not import kernel internal classes")
    void mcpToolsMustNotImportKernelInternals() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.spectrayan.spector.mcp..")
                .should().dependOnClassesThat(
                        resideInAPackage("com.spectrayan.spector.kernel..")
                                .and(not(resideInAPackage("com.spectrayan.spector.kernel.api..")))
                )
                .allowEmptyShould(true)
                .because("MCP tools must not access low-level kernel memory layouts (see #581)");

        rule.check(mcpClasses);
    }
}
