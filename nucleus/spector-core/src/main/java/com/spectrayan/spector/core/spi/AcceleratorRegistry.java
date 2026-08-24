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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Discovers, manages, and dispatches to registered {@link ComputeAccelerator} implementations.
 *
 * <p>Uses {@link ServiceLoader} to dynamically locate all {@link ComputeAccelerator}
 * providers on the classpath. Providers are sorted by {@link ComputeAccelerator#priority()}
 * descending, with the highest-priority available accelerator selected as primary.</p>
 *
 * <h3>Automatic Fallback</h3>
 * <p>If a hardware accelerator encounters an unexpected runtime error during compute,
 * the registry catches the exception, logs a diagnostic warning, and transparently
 * falls back to {@link CpuSimdSimilarityKernel}. Higher-level modules (memory, index)
 * never receive hardware-specific exceptions.</p>
 *
 * <h3>Configuration</h3>
 * <p>The minimum batch threshold for GPU offloading can be configured via the
 * {@code spector.hardware.gpu.batch-threshold} system property (default: 10,000).</p>
 */
public final class AcceleratorRegistry {

    private static final Logger log = LoggerFactory.getLogger(AcceleratorRegistry.class);

    /** System property key for overriding the GPU batch threshold. */
    public static final String GPU_BATCH_THRESHOLD_PROPERTY = "spector.hardware.gpu.batch-threshold";

    /** Default batch threshold when no custom property is set. */
    public static final int DEFAULT_BATCH_THRESHOLD = 10_000;

    private static final ReentrantLock INIT_LOCK = new ReentrantLock();
    private static volatile RegistryState state;

    private AcceleratorRegistry() {
    }

    private static final class RegistryState {
        final List<ComputeAccelerator> availableAccelerators;
        final ComputeAccelerator primaryAccelerator;
        final CpuSimdAccelerator fallbackAccelerator;
        final SimilarityKernel smartSimilarityKernel;

        RegistryState(List<ComputeAccelerator> availableAccelerators,
                      ComputeAccelerator primaryAccelerator,
                      CpuSimdAccelerator fallbackAccelerator) {
            this.availableAccelerators = availableAccelerators;
            this.primaryAccelerator = primaryAccelerator;
            this.fallbackAccelerator = fallbackAccelerator;
            this.smartSimilarityKernel = new SmartSimilarityKernel(primaryAccelerator);
        }
    }

    private static RegistryState getState() {
        RegistryState localState = state;
        if (localState != null) {
            return localState;
        }

        INIT_LOCK.lock();
        try {
            if (state != null) {
                return state;
            }
            state = initialize();
            return state;
        } finally {
            INIT_LOCK.unlock();
        }
    }

    private static RegistryState initialize() {
        List<ComputeAccelerator> discovered = new ArrayList<>();
        CpuSimdAccelerator cpuFallback = null;

        ServiceLoader<ComputeAccelerator> loader = ServiceLoader.load(ComputeAccelerator.class);
        for (ComputeAccelerator acc : loader) {
            try {
                if (acc instanceof CpuSimdAccelerator simd) {
                    cpuFallback = simd;
                }
                if (acc.isAvailable()) {
                    discovered.add(acc);
                    log.debug("Discovered available compute accelerator: {} (priority={})", acc.name(), acc.priority());
                } else {
                    log.debug("Discovered compute accelerator {} but it is not available on this host", acc.name());
                }
            } catch (Throwable t) {
                log.warn("Failed to query compute accelerator {}: {}", acc.name(), t.getMessage());
            }
        }

        if (cpuFallback == null) {
            cpuFallback = new CpuSimdAccelerator();
            discovered.add(cpuFallback);
        }

        discovered.sort(Comparator.comparingInt(ComputeAccelerator::priority).reversed());
        ComputeAccelerator primary = discovered.isEmpty() ? cpuFallback : discovered.get(0);

        log.info("AcceleratorRegistry initialized: primary='{}' (total available={})", primary.name(), discovered.size());
        return new RegistryState(List.copyOf(discovered), primary, cpuFallback);
    }

    /**
     * Returns the configured or default batch threshold for accelerator offload.
     *
     * @return batch threshold
     */
    public static int getBatchThreshold() {
        String prop = System.getProperty(GPU_BATCH_THRESHOLD_PROPERTY);
        if (prop != null && !prop.isBlank()) {
            try {
                return Integer.parseInt(prop.trim());
            } catch (NumberFormatException e) {
                log.warn("Invalid {} property '{}', using default {}", GPU_BATCH_THRESHOLD_PROPERTY, prop, DEFAULT_BATCH_THRESHOLD);
            }
        }
        return DEFAULT_BATCH_THRESHOLD;
    }

    /**
     * Returns the name of the currently active primary compute accelerator.
     *
     * @return primary accelerator name (e.g. "cuda", "cpu-simd")
     */
    public static String getActiveAcceleratorName() {
        return getState().primaryAccelerator.name();
    }

    /**
     * Returns the primary compute accelerator instance.
     *
     * @return primary accelerator
     */
    public static ComputeAccelerator getPrimaryAccelerator() {
        return getState().primaryAccelerator;
    }

    /**
     * Returns a hardware-transparent {@link SimilarityKernel} that automatically routes
     * batch operations to the primary accelerator when batch thresholds are met,
     * with automatic CPU SIMD fallback.
     *
     * @return smart similarity kernel
     */
    public static SimilarityKernel getSimilarityKernel() {
        return getState().smartSimilarityKernel;
    }

    /**
     * Returns the best available kernel for the given kernel type.
     *
     * @param <T> kernel type
     * @param kernelType class of the requested kernel interface
     * @return kernel instance, or null if unsupported
     */
    public static <T extends ComputeKernel> T getKernel(Class<T> kernelType) {
        Objects.requireNonNull(kernelType, "kernelType must not be null");
        if (kernelType == SimilarityKernel.class) {
            @SuppressWarnings("unchecked")
            T kernel = (T) getSimilarityKernel();
            return kernel;
        }

        RegistryState localState = getState();
        T kernel = localState.primaryAccelerator.getKernel(kernelType);
        if (kernel != null) {
            return kernel;
        }
        return localState.fallbackAccelerator.getKernel(kernelType);
    }

    /**
     * Resets the registry state (forces reload on next access). Primarily for testing.
     */
    public static void reset() {
        INIT_LOCK.lock();
        try {
            state = null;
        } finally {
            INIT_LOCK.unlock();
        }
    }

    /**
     * Hardware-transparent similarity kernel implementation that dispatches based on batch size.
     */
    private static final class SmartSimilarityKernel implements SimilarityKernel {

        private final ComputeAccelerator accelerator;
        private final SimilarityKernel acceleratorKernel;
        private final SimilarityKernel cpuKernel = CpuSimdSimilarityKernel.INSTANCE;

        SmartSimilarityKernel(ComputeAccelerator accelerator) {
            this.accelerator = accelerator;
            this.acceleratorKernel = (accelerator != null) ? accelerator.getKernel(SimilarityKernel.class) : null;
        }

        private boolean shouldRouteToAccelerator(int numVectors, int dimensions) {
            if (acceleratorKernel == null || accelerator == null || !accelerator.isAvailable()) {
                return false;
            }
            if (accelerator instanceof CpuSimdAccelerator) {
                return false;
            }
            int threshold = getBatchThreshold();
            int acceleratorMin = accelerator.minimumBatchSize(dimensions);
            int effectiveMin = Math.min(threshold, acceleratorMin);
            return numVectors >= effectiveMin;
        }

        @Override
        public float[] batchCosineSimilarity(float[] query, float[] database, int numVectors, int dimensions) {
            if (shouldRouteToAccelerator(numVectors, dimensions)) {
                try {
                    return acceleratorKernel.batchCosineSimilarity(query, database, numVectors, dimensions);
                } catch (Throwable t) {
                    log.warn("Accelerator '{}' failed on batchCosineSimilarity, falling back to CPU SIMD: {}",
                            accelerator.name(), t.getMessage());
                    log.debug("Accelerator failure details", t);
                }
            }
            return cpuKernel.batchCosineSimilarity(query, database, numVectors, dimensions);
        }

        @Override
        public float[] batchDotProduct(float[] query, float[] database, int numVectors, int dimensions) {
            if (shouldRouteToAccelerator(numVectors, dimensions)) {
                try {
                    return acceleratorKernel.batchDotProduct(query, database, numVectors, dimensions);
                } catch (Throwable t) {
                    log.warn("Accelerator '{}' failed on batchDotProduct, falling back to CPU SIMD: {}",
                            accelerator.name(), t.getMessage());
                    log.debug("Accelerator failure details", t);
                }
            }
            return cpuKernel.batchDotProduct(query, database, numVectors, dimensions);
        }

        @Override
        public float[] batchEuclideanDistance(float[] query, float[] database, int numVectors, int dimensions) {
            if (shouldRouteToAccelerator(numVectors, dimensions)) {
                try {
                    return acceleratorKernel.batchEuclideanDistance(query, database, numVectors, dimensions);
                } catch (Throwable t) {
                    log.warn("Accelerator '{}' failed on batchEuclideanDistance, falling back to CPU SIMD: {}",
                            accelerator.name(), t.getMessage());
                    log.debug("Accelerator failure details", t);
                }
            }
            return cpuKernel.batchEuclideanDistance(query, database, numVectors, dimensions);
        }
    }
}
