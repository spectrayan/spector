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
 * Micrometer Observation conventions, context models, and synaptic relay interceptors for Spector.
 *
 * <p>Key components:
 * <ul>
 *   <li>{@link com.spectrayan.spector.metrics.observation.ObservableRelay} — Micrometer Observation decorator for {@code SynapticRelay}</li>
 *   <li>{@link com.spectrayan.spector.metrics.observation.MemoryObservationContext} — Context holder for low/high cardinality key-values</li>
 *   <li>{@link com.spectrayan.spector.metrics.observation.DefaultSpectorObservationConvention} — Standard observation naming and tagging convention</li>
 *   <li>{@link com.spectrayan.spector.metrics.observation.MicrometerMemoryObservationHook} — Bridge from SPI observation hooks to Micrometer</li>
 * </ul>
 * </p>
 */
package com.spectrayan.spector.metrics.observation;
