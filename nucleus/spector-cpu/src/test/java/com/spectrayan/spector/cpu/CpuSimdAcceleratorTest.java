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
package com.spectrayan.spector.cpu;

import com.spectrayan.spector.core.spi.ComputeKernel;
import com.spectrayan.spector.core.spi.HnswCandidateKernel;
import com.spectrayan.spector.core.spi.MaxSimKernel;
import com.spectrayan.spector.core.spi.SimilarityKernel;
import com.spectrayan.spector.core.spi.SvasqDistanceKernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CpuSimdAcceleratorTest {

    private final CpuSimdAccelerator accelerator = new CpuSimdAccelerator();

    @Test
    @DisplayName("CpuSimdAccelerator properties and kernel capabilities")
    void testAcceleratorCapabilities() {
        assertThat(accelerator.name()).isEqualTo("cpu-simd");
        assertThat(accelerator.isAvailable()).isTrue();
        assertThat(accelerator.priority()).isEqualTo(0);
        assertThat(accelerator.minimumBatchSize(128)).isEqualTo(1);

        assertThat(accelerator.getKernel(SimilarityKernel.class)).isNotNull();
        assertThat(accelerator.getKernel(HnswCandidateKernel.class)).isNotNull();
        assertThat(accelerator.getKernel(SvasqDistanceKernel.class)).isNotNull();
        assertThat(accelerator.getKernel(MaxSimKernel.class)).isNotNull();
    }
}
