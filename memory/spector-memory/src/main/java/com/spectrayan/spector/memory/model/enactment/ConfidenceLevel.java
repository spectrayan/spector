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
package com.spectrayan.spector.memory.model.enactment;

/**
 * Epistemic confidence levels for persona enactment (ADR-0032).
 */
public enum ConfidenceLevel {
    /** Grounded in strong procedural playbooks or waking episodic majority. */
    EVIDENCED,
    /** Conflicting evidence resolved via Expected Free Energy policy selection. */
    MIXED,
    /** Thin or missing memory precedents; inferred from baseline priors with mandatory hedging. */
    INFERRED
}
