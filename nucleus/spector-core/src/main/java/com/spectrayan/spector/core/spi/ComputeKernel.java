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
 * Marker interface for all hardware-accelerated compute kernel contracts.
 *
 * <p>Each kernel sub-interface defines a specific category of operations
 * (e.g., batch similarity, matrix multiplication, quantized distance).
 * {@link ComputeAccelerator} implementations declare which kernels they
 * support via {@link ComputeAccelerator#getKernel(Class)}.</p>
 *
 * <h3>Extensibility</h3>
 * <p>Adding a new operation category corresponds to adding a new {@code ComputeKernel}
 * sub-interface without modifying existing accelerator implementations (Open/Closed Principle).</p>
 */
public interface ComputeKernel {
}
