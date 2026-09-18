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
package com.spectrayan.spector.memory.model;

/**
 * Communication style archetype — how the individual typically processes
 * and exchanges information.
 *
 * <h3>Cognitive Relevance</h3>
 * <p>Communication style influences how memories are organized and retrieved:</p>
 * <ul>
 *   <li><b>Analytical</b> — structured, sequential encoding; strong for procedural chains</li>
 *   <li><b>Intuitive</b> — holistic, pattern-based encoding; strong for lateral associations</li>
 *   <li><b>Functional</b> — process-oriented encoding; strong for step-by-step recall</li>
 *   <li><b>Assertive/Passive/Aggressive/Passive-Aggressive</b> — social-emotional encoding style</li>
 * </ul>
 *
 * <h3>Schema Origin</h3>
 * <p>Mirrors {@code consciousness/social/CommunicationStyle.yaml} with identical enum values.</p>
 *
 * <h3>Future Use</h3>
 * <p>Currently informational. Future: may modulate Hebbian co-activation patterns
 * (Analytical → structured graph, Intuitive → lateral retrieval preference).</p>
 */
public enum CommunicationStyle {

    /** Direct, clear communication — structured encoding. */
    ASSERTIVE,

    /** Accommodating, avoids conflict — social-context-weighted encoding. */
    PASSIVE,

    /** Confrontational — high-arousal interpersonal encoding. */
    AGGRESSIVE,

    /** Indirect resistance — mixed-valence social encoding. */
    PASSIVE_AGGRESSIVE,

    /** Data-driven, systematic — strong for procedural and structured memories. */
    ANALYTICAL,

    /** Big-picture, pattern-recognition — strong for lateral associations. */
    INTUITIVE,

    /** Process-oriented, step-by-step — strong for operational/sequential recall. */
    FUNCTIONAL
}
