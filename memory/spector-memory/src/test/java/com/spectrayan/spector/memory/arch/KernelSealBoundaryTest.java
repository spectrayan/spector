/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.arch;

import com.spectrayan.spector.test.arch.SealRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Validates that memory/spector-memory adheres to kernel isolation boundaries (R3.5, R11.6).
 *
 * <p>Enforces that Java FFM (java.lang.foreign) is sealed inside spector-kernel.
 * Pre-existing usages scheduled for conversion in Groups 5-8 are explicitly excluded with
 * their target group references per R11.6.</p>
 */
class KernelSealBoundaryTest {

    private static JavaClasses memoryClasses;

    @BeforeAll
    static void importClasses() {
        memoryClasses = SealRules.importedOrFail("com.spectrayan.spector.memory", 100);
    }

    @Test
    @DisplayName("Clean packages (bootstrap, persist, consolidation) must have zero java.lang.foreign dependencies")
    void cleanPackagesMustHaveZeroForeignDependencies() {
        JavaClasses bootstrapClasses = SealRules.importedOrFail("com.spectrayan.spector.memory.bootstrap", 1);
        SealRules.NO_FOREIGN_OUTSIDE_KERNEL.check(bootstrapClasses);

        JavaClasses persistClasses = SealRules.importedOrFail("com.spectrayan.spector.memory.persist", 5);
        SealRules.NO_FOREIGN_OUTSIDE_KERNEL.check(persistClasses);

        JavaClasses consolidationClasses = SealRules.importedOrFail("com.spectrayan.spector.memory.cortex.consolidation", 5);
        SealRules.NO_FOREIGN_OUTSIDE_KERNEL.check(consolidationClasses);
    }

    @Test
    @DisplayName("No foreign imports outside kernel, with explicit triage for scheduled conversions (R11.6)")
    void noForeignOutsideKernelWithTriage() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.spectrayan.spector.memory..")
                .and(not(nameMatching(".*("
                        // Group 6: Scan Pipeline scheduled conversions
                        + "CognitiveScorer|SemanticRecallStrategy|SemanticDeduplicator|MemoryBM25Index|"
                        + "GraphExpansionStage|RecallCandidateGatherer|"
                        + "ParallelScanEmitter|ScanEmitter|SequentialScanEmitter|SlabScoreFunction|DreamJournalMemory|"
                        // Group 7: Graph & Table APIs scheduled conversions
                        + "IndexEntryMemory|EntityDirectory|HebbianGraph|SynapticDecayModulator|"
                        + "TemporalFact|TemporalKnowledgeGraph"
                        + ").*")))
                .should().dependOnClassesThat().resideInAnyPackage("java.lang.foreign..")
                .because("Panama I/O is sealed inside spector-kernel (spec R3.5, R11.6)");

        rule.check(memoryClasses);
    }

    @Test
    @DisplayName("No Arena is constructed outside the kernel, with explicit triage for scheduled conversions (R8.2)")
    void noArenaConstructedOutsideKernel() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.spectrayan.spector.memory..")
                .and(not(nameMatching(".*("
                        // Group 6: Scan Pipeline scheduled conversions
                        + "CognitiveScorer|SemanticRecallStrategy|SemanticDeduplicator|MemoryBM25Index|"
                        + "GraphExpansionStage|RecallCandidateGatherer|"
                        + "ParallelScanEmitter|ScanEmitter|SequentialScanEmitter|SlabScoreFunction|DreamJournalMemory|"
                        // Group 7: Graph & Table APIs scheduled conversions
                        + "IndexEntryMemory|EntityDirectory|HebbianGraph|SynapticDecayModulator|"
                        + "TemporalFact|TemporalKnowledgeGraph"
                        + ").*")))
                .should().callMethod(java.lang.foreign.Arena.class, "ofShared")
                .orShould().callMethod(java.lang.foreign.Arena.class, "ofConfined")
                .orShould().callMethod(java.lang.foreign.Arena.class, "ofAuto")
                .orShould().callMethod(java.lang.foreign.Arena.class, "global")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.lang.foreign.Arena")
                .because("no code outside the kernel shall construct or depend on an Arena (spec R8.2)");

        rule.check(memoryClasses);
    }
}
