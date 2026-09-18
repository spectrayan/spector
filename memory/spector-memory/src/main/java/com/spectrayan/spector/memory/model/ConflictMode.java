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
 * Controls how the cognitive recall pathway handles contradicting bitemporal facts and claims.
 */
public enum ConflictMode {
    /**
     * Preserves all competing evidence versions and attaches epistemic entropy and action policies.
     */
    MULTI_EVIDENCE,

    /**
     * Resolves deterministically to the single highest-confidence fact.
     */
    HIGHEST_CONFIDENCE,

    /**
     * Drops any candidate with active contradictions (fail-closed security).
     */
    FAIL_CLOSED
}
