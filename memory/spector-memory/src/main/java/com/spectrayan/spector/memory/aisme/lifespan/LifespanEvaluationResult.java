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
package com.spectrayan.spector.memory.aisme.lifespan;

/**
 * Result of evaluating a memory record against the lifespan-adaptive retention policy.
 *
 * @param decision disposition action (RETAIN, CONSOLIDATE, PRUNE)
 * @param tier classified autobiographical tier (CORE, FLAVOUR, EPHEMERAL)
 * @param effectiveTau dynamic threshold \(\tau(t)\) active during evaluation
 * @param importance evaluated composite importance \(I(o_t)\)
 * @param flashbulbProtected whether the memory is protected by flashbulb / milestone invariant status
 */
public record LifespanEvaluationResult(
        LifespanRetentionDecision decision,
        LifespanTier tier,
        float effectiveTau,
        float importance,
        boolean flashbulbProtected
) {

    public enum LifespanRetentionDecision {
        /**
         * Retain in episodic memory with full fidelity.
         */
        RETAIN,

        /**
         * Consolidate into higher-order semantic summaries or gists before archival.
         */
        CONSOLIDATE,

        /**
         * Eligible for homeostatic tombstoning and partition compaction.
         */
        PRUNE
    }
}
