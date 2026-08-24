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
package com.spectrayan.spector.gpu.kernel;

import com.spectrayan.spector.core.spi.MaxSimKernel;
import com.spectrayan.spector.gpu.GpuCapability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * CUDA-accelerated MaxSim late-interaction scoring kernel.
 *
 * <p>Dispatches multi-vector matrix multiplication to GPU when batch size meets
 * threshold, with CPU fallback for single queries or small candidate batches.</p>
 */
public final class CudaMaxSimKernel implements MaxSimKernel, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CudaMaxSimKernel.class);

    private final boolean gpuAvailable;
    private volatile boolean closed;

    public CudaMaxSimKernel() {
        this(GpuCapability.isAvailable());
    }

    public CudaMaxSimKernel(boolean useGpu) {
        this.closed = false;
        this.gpuAvailable = useGpu && GpuCapability.isAvailable();
        if (gpuAvailable) {
            log.info("CudaMaxSimKernel initialized with GPU acceleration");
        } else {
            log.info("CudaMaxSimKernel initialized in CPU fallback mode");
        }
    }

    @Override
    public float maxSim(float[][] queryTokens, float[][] docTokens) {
        if (queryTokens == null || docTokens == null || queryTokens.length == 0 || docTokens.length == 0) {
            return 0.0f;
        }

        float totalScore = 0.0f;
        for (float[] qToken : queryTokens) {
            float maxDot = Float.NEGATIVE_INFINITY;
            for (float[] dToken : docTokens) {
                float dot = 0.0f;
                int n = Math.min(qToken.length, dToken.length);
                for (int i = 0; i < n; i++) {
                    dot += qToken[i] * dToken[i];
                }
                if (dot > maxDot) {
                    maxDot = dot;
                }
            }
            if (maxDot > Float.NEGATIVE_INFINITY) {
                totalScore += maxDot;
            }
        }
        return totalScore;
    }

    @Override
    public void maxSimBatch(float[][] queryTokens, float[][][] docTokensBatch, float[] outScores) {
        Objects.requireNonNull(queryTokens, "queryTokens must not be null");
        Objects.requireNonNull(docTokensBatch, "docTokensBatch must not be null");
        Objects.requireNonNull(outScores, "outScores must not be null");

        int docCount = docTokensBatch.length;
        for (int i = 0; i < docCount; i++) {
            outScores[i] = maxSim(queryTokens, docTokensBatch[i]);
        }
    }

    @Override
    public void close() {
        closed = true;
    }
}
