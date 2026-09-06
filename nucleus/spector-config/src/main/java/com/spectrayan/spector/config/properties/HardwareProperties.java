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
package com.spectrayan.spector.config.properties;

import static com.spectrayan.spector.config.SpectorPropertyConstants.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * Configuration properties POJO for Spector Hardware &amp; GPU Acceleration.
 */
public class HardwareProperties implements Serializable {

    private static final long serialVersionUID = 1L;

    private int gpuBatchThreshold = 32;
    private long gpuBatchMinWindowMs = DEFAULT_GPU_BATCH_MIN_WINDOW_MS;
    private long gpuBatchMaxWindowMs = DEFAULT_GPU_BATCH_MAX_WINDOW_MS;
    private int gpuBatchDefaultMaxBatch = DEFAULT_GPU_BATCH_DEFAULT_MAX_BATCH;
    private long gpuMemoryMinBudgetBytes = DEFAULT_GPU_MEMORY_MIN_BUDGET_BYTES;

    public HardwareProperties() {}

    public int getGpuBatchThreshold() { return gpuBatchThreshold; }
    public void setGpuBatchThreshold(int gpuBatchThreshold) { this.gpuBatchThreshold = gpuBatchThreshold; }

    public long getGpuBatchMinWindowMs() { return gpuBatchMinWindowMs; }
    public void setGpuBatchMinWindowMs(long gpuBatchMinWindowMs) { this.gpuBatchMinWindowMs = gpuBatchMinWindowMs; }

    public long getGpuBatchMaxWindowMs() { return gpuBatchMaxWindowMs; }
    public void setGpuBatchMaxWindowMs(long gpuBatchMaxWindowMs) { this.gpuBatchMaxWindowMs = gpuBatchMaxWindowMs; }

    public int getGpuBatchDefaultMaxBatch() { return gpuBatchDefaultMaxBatch; }
    public void setGpuBatchDefaultMaxBatch(int gpuBatchDefaultMaxBatch) { this.gpuBatchDefaultMaxBatch = gpuBatchDefaultMaxBatch; }

    public long getGpuMemoryMinBudgetBytes() { return gpuMemoryMinBudgetBytes; }
    public void setGpuMemoryMinBudgetBytes(long gpuMemoryMinBudgetBytes) { this.gpuMemoryMinBudgetBytes = gpuMemoryMinBudgetBytes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HardwareProperties that = (HardwareProperties) o;
        return gpuBatchThreshold == that.gpuBatchThreshold &&
                gpuBatchMinWindowMs == that.gpuBatchMinWindowMs &&
                gpuBatchMaxWindowMs == that.gpuBatchMaxWindowMs &&
                gpuBatchDefaultMaxBatch == that.gpuBatchDefaultMaxBatch &&
                gpuMemoryMinBudgetBytes == that.gpuMemoryMinBudgetBytes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(gpuBatchThreshold, gpuBatchMinWindowMs, gpuBatchMaxWindowMs,
                gpuBatchDefaultMaxBatch, gpuMemoryMinBudgetBytes);
    }
}
