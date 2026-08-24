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

import com.spectrayan.spector.core.spi.ComputeAccelerator;
import com.spectrayan.spector.core.spi.ComputeKernel;
import com.spectrayan.spector.core.spi.HnswCandidateKernel;
import com.spectrayan.spector.core.spi.MaxSimKernel;
import com.spectrayan.spector.core.spi.SimilarityKernel;
import com.spectrayan.spector.core.spi.SvasqDistanceKernel;
import com.spectrayan.spector.cpu.kernel.CpuSimdCandidateKernel;
import com.spectrayan.spector.cpu.kernel.CpuSimdMaxSimKernel;
import com.spectrayan.spector.cpu.kernel.CpuSimdSimilarityKernel;
import com.spectrayan.spector.cpu.kernel.CpuSimdSvasqKernel;

/**
 * CPU SIMD compute accelerator provider (Priority: 0).
 *
 * <p>Implements {@link ComputeAccelerator} using the Java 25 Panama Vector API
 * (AVX-512, AVX2, ARM NEON). This provider is always available on host CPUs.</p>
 */
public final class CpuSimdAccelerator implements ComputeAccelerator {

    private final SimilarityKernel similarityKernel = CpuSimdSimilarityKernel.INSTANCE;
    private final HnswCandidateKernel candidateKernel = CpuSimdCandidateKernel.INSTANCE;
    private final SvasqDistanceKernel svasqKernel = CpuSimdSvasqKernel.INSTANCE;
    private final MaxSimKernel maxSimKernel = CpuSimdMaxSimKernel.INSTANCE;

    public CpuSimdAccelerator() {
    }

    @Override
    public String name() {
        return "cpu-simd";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public int minimumBatchSize(int dimensions) {
        return 1;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ComputeKernel> T getKernel(Class<T> kernelType) {
        if (kernelType == SimilarityKernel.class) {
            return (T) similarityKernel;
        }
        if (kernelType == HnswCandidateKernel.class) {
            return (T) candidateKernel;
        }
        if (kernelType == SvasqDistanceKernel.class) {
            return (T) svasqKernel;
        }
        if (kernelType == MaxSimKernel.class) {
            return (T) maxSimKernel;
        }
        return null;
    }
}
