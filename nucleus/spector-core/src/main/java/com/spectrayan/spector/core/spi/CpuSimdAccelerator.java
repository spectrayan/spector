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

/**
 * Default CPU SIMD compute accelerator using the Java Vector API.
 *
 * <p>Always available with priority 0. This is the built-in baseline accelerator
 * for all platforms, and serves as the fallback whenever specialized accelerators
 * are unavailable or when batch sizes fall below GPU break-even thresholds.</p>
 */
public final class CpuSimdAccelerator implements ComputeAccelerator {

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

    @SuppressWarnings("unchecked")
    @Override
    public <T extends ComputeKernel> T getKernel(Class<T> kernelType) {
        if (kernelType == SimilarityKernel.class) {
            return (T) CpuSimdSimilarityKernel.INSTANCE;
        }
        return null;
    }

    @Override
    public void close() {
        // No native resources to release
    }
}
