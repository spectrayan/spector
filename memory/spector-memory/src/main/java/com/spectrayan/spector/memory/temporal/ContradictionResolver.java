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
package com.spectrayan.spector.memory.temporal;

import java.util.List;

/**
 * Strategy interface for resolving contradictions between overlapping or mutually exclusive temporal facts.
 * In a biological system, this is akin to cognitive conflict resolution where stronger or more recent
 * memory traces override weaker or outdated ones.
 */
@FunctionalInterface
public interface ContradictionResolver {
    /**
     * Resolves a contradiction by selecting the winning fact from a list of conflicting facts.
     *
     * @param conflicting the list of conflicting temporal facts
     * @return the resolved (winning) temporal fact
     */
    TemporalFact resolve(List<TemporalFact> conflicting);
}
