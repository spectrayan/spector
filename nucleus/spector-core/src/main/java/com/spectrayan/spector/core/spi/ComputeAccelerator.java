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
 * Service Provider Interface for hardware compute accelerators.
 *
 * <p>Implementations are discovered at runtime via {@link java.util.ServiceLoader}.
 * Each accelerator declares its identity, availability, priority, and which
 * {@link ComputeKernel} operations it supports.</p>
 *
 * <h3>Provider Selection</h3>
 * <p>When multiple accelerators are available, the {@link AcceleratorRegistry}
 * selects the one with the highest {@link #priority()} that reports
 * {@link #isAvailable()} as {@code true}.</p>
 *
 * <h3>Registration</h3>
 * <p>Place a file at
 * {@code META-INF/services/com.spectrayan.spector.core.spi.ComputeAccelerator}
 * containing the fully qualified class name of your implementation.</p>
 *
 * @see AcceleratorRegistry
 * @see ComputeKernel
 * @see SimilarityKernel
 */
public interface ComputeAccelerator extends AutoCloseable {

    /**
     * Returns the unique accelerator name (e.g., "cuda", "cpu-simd", "rocm", "metal").
     *
     * @return accelerator name
     */
    String name();

    /**
     * Checks if the underlying hardware accelerator is available at runtime.
     *
     * @return true if available, false otherwise
     */
    boolean isAvailable();

    /**
     * Returns selection priority. Higher values are preferred.
     * <ul>
     *   <li>{@code 0} — CPU SIMD (default, always available)</li>
     *   <li>{@code 100} — CUDA GPU</li>
     *   <li>{@code 200} — Future specialized hardware</li>
     * </ul>
     *
     * @return priority integer
     */
    int priority();

    /**
     * Returns the minimum batch size (number of vectors) at which this
     * accelerator outperforms CPU SIMD for the given dimensionality.
     *
     * <p>Below this threshold, the dispatcher routes to CPU SIMD
     * regardless of accelerator availability.</p>
     *
     * @param dimensions vector dimensionality (affects PCIe vs compute tradeoff)
     * @return minimum batch size for this accelerator to be beneficial
     */
    int minimumBatchSize(int dimensions);

    /**
     * Returns the kernel implementation for the given type, or {@code null}
     * if this accelerator does not support that kernel category.
     *
     * <p>This capability-query pattern follows JCA's {@code Provider.getService()}
     * and satisfies the Interface Segregation Principle — accelerators are never
     * forced to implement kernel types they don't support.</p>
     *
     * @param <T> the kernel interface type
     * @param kernelType the kernel interface class (e.g., {@code SimilarityKernel.class})
     * @return the kernel implementation, or {@code null} if unsupported
     */
    <T extends ComputeKernel> T getKernel(Class<T> kernelType);

    /**
     * Lifecycle: called when the accelerator is being shut down.
     */
    @Override
    default void close() {
    }
}
