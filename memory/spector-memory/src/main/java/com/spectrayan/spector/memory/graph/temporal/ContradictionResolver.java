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
package com.spectrayan.spector.memory.graph.temporal;

import com.spectrayan.spector.kernel.store.TemporalFact;

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
