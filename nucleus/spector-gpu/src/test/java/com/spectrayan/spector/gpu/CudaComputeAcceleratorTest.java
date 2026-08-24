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
package com.spectrayan.spector.gpu;

import com.spectrayan.spector.core.spi.AcceleratorRegistry;
import com.spectrayan.spector.core.spi.ComputeAccelerator;
import com.spectrayan.spector.core.spi.SimilarityKernel;
import com.spectrayan.spector.gpu.kernel.CudaSimilarityKernel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CudaComputeAcceleratorTest {

    @BeforeEach
    @AfterEach
    void reset() {
        AcceleratorRegistry.reset();
    }

    @Test
    @DisplayName("CudaComputeAccelerator properties are correctly configured")
    void testProperties() {
        CudaComputeAccelerator accelerator = new CudaComputeAccelerator();
        assertThat(accelerator.name()).isEqualTo("cuda");
        assertThat(accelerator.priority()).isEqualTo(100);
        assertThat(accelerator.minimumBatchSize(384)).isGreaterThan(1000);
    }

    @Test
    @DisplayName("CudaComputeAccelerator provides SimilarityKernel")
    void testGetKernel() {
        CudaComputeAccelerator accelerator = new CudaComputeAccelerator();
        SimilarityKernel kernel = accelerator.getKernel(SimilarityKernel.class);
        assertThat(kernel).isNotNull();
    }

    @Test
    @DisplayName("CudaSimilarityKernel computes valid cosine, dot, and euclidean results")
    void testCudaSimilarityKernel() {
        CudaSimilarityKernel kernel = new CudaSimilarityKernel();

        float[] query = new float[32];
        query[0] = 1.0f;
        query[1] = 2.0f;

        float[] database = new float[32 * 2];
        database[0] = 1.0f;
        database[1] = 2.0f;
        database[32] = 3.0f;
        database[33] = 4.0f;

        float[] dots = kernel.dotProduct(query, database, 2, 32);
        assertThat(dots).hasSize(2);
        assertThat(dots[0]).isCloseTo(5.0f, within(1e-4f));
        assertThat(dots[1]).isCloseTo(11.0f, within(1e-4f));

        float[] cosines = kernel.cosineSimilarity(query, database, 2, 32);
        assertThat(cosines).hasSize(2);
        assertThat(cosines[0]).isCloseTo(1.0f, within(1e-4f));

        float[] dists = kernel.euclideanDistance(query, database, 2, 32);
        assertThat(dists).hasSize(2);
        assertThat(dists[0]).isCloseTo(0.0f, within(1e-4f));
    }

    @Test
    @DisplayName("AcceleratorRegistry discovers CudaComputeAccelerator via ServiceLoader")
    void testSpiDiscovery() {
        ComputeAccelerator primary = AcceleratorRegistry.getPrimaryAccelerator();
        assertThat(primary).isNotNull();
        if (GpuCapability.isAvailable()) {
            assertThat(AcceleratorRegistry.getActiveAcceleratorName()).isEqualTo("cuda");
        } else {
            assertThat(AcceleratorRegistry.getActiveAcceleratorName()).isEqualTo("cpu-simd");
        }
    }
}
