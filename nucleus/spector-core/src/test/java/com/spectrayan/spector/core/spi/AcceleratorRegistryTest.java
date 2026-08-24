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
package com.spectrayan.spector.core.spi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcceleratorRegistryTest {

    @BeforeEach
    @AfterEach
    void resetRegistry() {
        System.clearProperty(AcceleratorRegistry.GPU_BATCH_THRESHOLD_PROPERTY);
        AcceleratorRegistry.reset();
    }

    @Test
    @DisplayName("discovers default CpuSimdAccelerator")
    void testDefaultDiscovery() {
        String activeName = AcceleratorRegistry.getActiveAcceleratorName();
        assertThat(activeName).isNotEmpty();

        ComputeAccelerator primary = AcceleratorRegistry.getPrimaryAccelerator();
        assertThat(primary).isNotNull();
        assertThat(primary.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("getSimilarityKernel returns non-null kernel")
    void testGetSimilarityKernel() {
        SimilarityKernel kernel = AcceleratorRegistry.getSimilarityKernel();
        assertThat(kernel).isNotNull();

        float[] query = {1.0f, 2.0f};
        float[] db = {1.0f, 2.0f};
        float[] dots = kernel.dotProduct(query, db, 1, 2);
        assertThat(dots).containsExactly(5.0f);
    }

    @Test
    @DisplayName("getKernel with SimilarityKernel class returns smart kernel")
    void testGetKernelSimilarity() {
        SimilarityKernel kernel = AcceleratorRegistry.getKernel(SimilarityKernel.class);
        assertThat(kernel).isNotNull();
    }

    @Test
    @DisplayName("getKernel with unsupported type returns null")
    void testGetKernelUnsupported() {
        interface UnsupportedKernel extends ComputeKernel {}
        UnsupportedKernel kernel = AcceleratorRegistry.getKernel(UnsupportedKernel.class);
        assertThat(kernel).isNull();
    }

    @Test
    @DisplayName("getKernel validates null argument")
    void testGetKernelNull() {
        assertThatThrownBy(() -> AcceleratorRegistry.getKernel(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("reads configurable batch threshold property")
    void testConfigurableThreshold() {
        assertThat(AcceleratorRegistry.getBatchThreshold())
                .isEqualTo(AcceleratorRegistry.DEFAULT_BATCH_THRESHOLD);

        System.setProperty(AcceleratorRegistry.GPU_BATCH_THRESHOLD_PROPERTY, "5000");
        assertThat(AcceleratorRegistry.getBatchThreshold()).isEqualTo(5000);

        System.setProperty(AcceleratorRegistry.GPU_BATCH_THRESHOLD_PROPERTY, "invalid");
        assertThat(AcceleratorRegistry.getBatchThreshold())
                .isEqualTo(AcceleratorRegistry.DEFAULT_BATCH_THRESHOLD);
    }
}
