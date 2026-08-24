/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
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
