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
package com.spectrayan.spector.memory.synapse;

/**
 * Functional interface providing an O(1) associative prior A_g in [0, 1] for Phase 6 score fusion (MR-06).
 *
 * <h3>Biological Analog: Associative Co-Activation & STDP Prior</h3>
 * <p>Derives an early associative activation based on STDP predictive strength and co-activation degree.
 * Novel memories with no associative history return 0.0f and are NEVER eliminated by the prior.</p>
 */
@FunctionalInterface
public interface AssociativePriorProvider {

    /**
     * Evaluates the normalized associative activation A_g in [0.0, 1.0] for the given record.
     *
     * @param candidateOffset byte offset of the record in the memory segment
     * @param recordTags      synaptic tag bitmask of the record
     * @param ctx             pre-resolved query associative context
     * @return normalized activation in [0.0, 1.0], or 0.0f if no associative history exists
     */
    float priorFor(long candidateOffset, long recordTags, QueryAssociativeContext ctx);
}
