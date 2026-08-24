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
 * Vector similarity and distance functions with SIMD and quantized variants.
 *
 * <p>This package contains the core distance/similarity compute kernels used
 * for nearest-neighbor search, vector comparison, and index operations:</p>
 *
 * <ul>
 *   <li><b>Float32 kernels</b>: {@link com.spectrayan.spector.core.similarity.CosineSimilarity},
 *       {@link com.spectrayan.spector.core.similarity.DotProduct},
 *       {@link com.spectrayan.spector.core.similarity.EuclideanDistance}</li>
 *   <li><b>Quantized kernels</b>: {@link com.spectrayan.spector.core.similarity.QuantizedCosineSimilarity},
 *       {@link com.spectrayan.spector.core.similarity.QuantizedEuclideanDistance},
 *       {@link com.spectrayan.spector.core.similarity.QuantizedDotProduct},
 *       {@link com.spectrayan.spector.core.similarity.PackedDotProduct}</li>
 *   <li><b>Utilities</b>: {@link com.spectrayan.spector.core.similarity.VectorOps},
 *       {@link com.spectrayan.spector.core.similarity.SimilarityFunction}</li>
 * </ul>
 *
 * <p>Cognitive neuroscience kernels, expression generators, and privacy mechanisms
 * are in their own packages: {@code core.cognitive}, {@code core.expression},
 * and {@code core.privacy}.</p>
 */
package com.spectrayan.spector.core.similarity;
