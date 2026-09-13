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
package com.spectrayan.spector.kernel.valhalla;

import com.spectrayan.spector.commons.valhalla.ValueClassValidator;
import com.spectrayan.spector.kernel.id.MemoryId;
import com.spectrayan.spector.kernel.region.RegionSizeSpec;
import com.spectrayan.spector.kernel.store.ContinuityRecord;
import com.spectrayan.spector.kernel.store.EpisodicMemory;
import com.spectrayan.spector.kernel.store.HebbianEdge;
import com.spectrayan.spector.kernel.store.TemporalFact;
import com.spectrayan.spector.kernel.store.TemporalFactsMemory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class KernelValhallaReadinessTest {

    @Test
    @DisplayName("Spector Kernel value candidates pass JEP 390 / JEP 401 compliance audit")
    void testKernelValueCandidates() {
        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(HebbianEdge.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(TemporalFact.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(ContinuityRecord.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(MemoryId.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(EpisodicMemory.TurnHeaderSnapshot.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(TemporalFactsMemory.FactLogEntry.class))
                .doesNotThrowAnyException();

        assertThatCode(() -> ValueClassValidator.assertValueClassCompliant(RegionSizeSpec.class))
                .doesNotThrowAnyException();
    }
}
