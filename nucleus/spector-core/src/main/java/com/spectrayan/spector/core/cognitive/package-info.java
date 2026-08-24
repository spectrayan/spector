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

/**
 * Cognitive neuroscience compute kernels for the Spector memory architecture.
 *
 * <p>This package contains pure-math SIMD-accelerated kernels implementing
 * computational neuroscience models used by the higher-level memory subsystems
 * in {@code spector-memory}. All kernels are stateless, immutable, and
 * thread-safe.</p>
 *
 * <h3>Kernel Taxonomy</h3>
 * <ul>
 *   <li><b>Attractor networks</b>: {@link com.spectrayan.spector.core.cognitive.HopfieldKernel},
 *       {@link com.spectrayan.spector.core.cognitive.LsrHopfieldKernel}</li>
 *   <li><b>Free energy / active inference</b>: {@link com.spectrayan.spector.core.cognitive.FreeEnergyKernel},
 *       {@link com.spectrayan.spector.core.cognitive.ExpectedFreeEnergyKernel}</li>
 *   <li><b>Predictive coding</b>: {@link com.spectrayan.spector.core.cognitive.PredictiveCodingKernel}</li>
 *   <li><b>Temporal dynamics</b>: {@link com.spectrayan.spector.core.cognitive.BocpdKernel}</li>
 *   <li><b>Information integration</b>: {@link com.spectrayan.spector.core.cognitive.IntegratedInformationKernel}</li>
 *   <li><b>Neural manifold</b>: {@link com.spectrayan.spector.core.cognitive.NeuralManifoldDistance}</li>
 *   <li><b>Importance scoring</b>: {@link com.spectrayan.spector.core.cognitive.CompositeImportanceKernel}</li>
 * </ul>
 */
package com.spectrayan.spector.core.cognitive;
