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
package com.spectrayan.spector.kernel;

import com.spectrayan.spector.test.arch.SealRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Validates that spector-kernel satisfies the architectural sealing rules (R3.5, R5, R11.6).
 */
class KernelSealRulesTest {

    private static JavaClasses kernelClasses;

    @BeforeAll
    static void importKernelClasses() {
        kernelClasses = SealRules.importedOrFail("com.spectrayan.spector.kernel", 50);
    }

    @Test
    @DisplayName("Kernel classes must never depend on cognitive, memory, or config types (R5)")
    void kernelMustNotDependOnCognitiveTypes() {
        SealRules.KERNEL_HAS_NO_COGNITIVE_TYPES.check(kernelClasses);
    }

    @Test
    @DisplayName("Kernel API classes must never depend on internal store implementations (R4.6)")
    void apiMustNotDependOnStore() {
        SealRules.API_DOES_NOT_DEPEND_ON_STORE.check(kernelClasses);
    }

    @Test
    @DisplayName("Kernel score package must be pure mathematics free of storage and config (Improvement 2)")
    void scorePackageMustBePure() {
        SealRules.SCORE_PACKAGE_IS_PURE.check(kernelClasses);
    }
}
