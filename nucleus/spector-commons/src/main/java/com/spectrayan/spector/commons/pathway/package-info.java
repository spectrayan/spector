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
 * Provides the Cognitive Pathway and Synaptic Circuit Engine core interfaces, resilience primitives,
 * and execution diagnostics.
 *
 * <p>Key components:</p>
 * <ul>
 *   <li>{@link com.spectrayan.spector.commons.pathway.SynapticRelay} — Atomic single-responsibility processing stage.</li>
 *   <li>{@link com.spectrayan.spector.commons.pathway.CognitivePathway} — Sequential conductor with error policies ({@link com.spectrayan.spector.commons.pathway.ErrorPolicy#FAIL_FAST}, {@link com.spectrayan.spector.commons.pathway.ErrorPolicy#DEGRADE_GRACEFULLY}) and domain exception preservation.</li>
 *   <li>{@link com.spectrayan.spector.commons.pathway.GatedRelay} — Conditional execution based on {@link com.spectrayan.spector.commons.pathway.Specification} or predicates.</li>
 *   <li>{@link com.spectrayan.spector.commons.pathway.DivergentRelay} — Parallel multi-branch execution with per-branch error policies and fork/merge semantics.</li>
 *   <li>{@link com.spectrayan.spector.commons.pathway.CircuitBreakerRelay} — Non-blocking adaptive circuit breaker with cooldown recovery for protecting external/downstream endpoints.</li>
 *   <li>{@link com.spectrayan.spector.commons.pathway.ConsolidationRelay} — Asynchronous fire-and-forget background worker.</li>
 *   <li>{@link com.spectrayan.spector.commons.pathway.TraceableSignal} &amp; {@link com.spectrayan.spector.commons.pathway.RelayTrace} — Zero-overhead step-by-step diagnostic execution tracing.</li>
 * </ul>
 */
package com.spectrayan.spector.commons.pathway;
