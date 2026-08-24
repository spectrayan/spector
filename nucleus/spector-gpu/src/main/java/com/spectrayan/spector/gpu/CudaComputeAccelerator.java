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

import com.spectrayan.spector.core.spi.ComputeAccelerator;
import com.spectrayan.spector.core.spi.ComputeKernel;
import com.spectrayan.spector.core.spi.SimilarityKernel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReentrantLock;

/**
 * CUDA GPU compute accelerator using Panama FFM and PTX kernels.
 *
 * <p>Registered via Java SPI with priority 100. Discovered automatically by
 * {@link com.spectrayan.spector.core.spi.AcceleratorRegistry} when {@code spector-gpu}
 * is on the classpath and an NVIDIA GPU with CUDA driver is present.</p>
 */
public final class CudaComputeAccelerator implements ComputeAccelerator {

    private static final Logger log = LoggerFactory.getLogger(CudaComputeAccelerator.class);

    private final ReentrantLock lock = new ReentrantLock();
    private volatile CudaSimilarityKernel similarityKernel;

    @Override
    public String name() {
        return "cuda";
    }

    @Override
    public boolean isAvailable() {
        return GpuCapability.isAvailable();
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public int minimumBatchSize(int dimensions) {
        // PCIe transfer overhead dominates for small batches.
        // Breakeven is ~10K vectors for 384-dim, scaling down for higher dimensions.
        return Math.max(1024, 10_000 - (dimensions * 4));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends ComputeKernel> T getKernel(Class<T> kernelType) {
        if (kernelType == SimilarityKernel.class) {
            if (similarityKernel == null) {
                lock.lock();
                try {
                    if (similarityKernel == null) {
                        similarityKernel = new CudaSimilarityKernel();
                    }
                } finally {
                    lock.unlock();
                }
            }
            return (T) similarityKernel;
        }
        return null;
    }

    @Override
    public void close() {
        log.info("CudaComputeAccelerator closed");
    }
}
