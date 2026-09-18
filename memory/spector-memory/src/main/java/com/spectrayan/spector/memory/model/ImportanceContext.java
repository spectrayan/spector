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
import com.spectrayan.spector.kernel.api.MemoryType;

import java.util.List;
import com.spectrayan.spector.memory.neuromod.neurodivergent.RememberHints;

/**
 * Consolidates all inputs required for computing the importance of a memory.
 *
 * @param text            the text content of the memory
 * @param vector          the vector embeddings for the memory text
 * @param hints           the ingestion hints containing ICNU (Interest, Competence, Novelty, Urgency) values, may be null
 * @param salienceProfile the salience profile associated with the memory context, may be null
 * @param targetTier      the target memory tier, may be null
 * @param nearestDistance the distance to the nearest existing memory
 * @param noveltyZScore   the novelty z-score based on existing memories
 * @param readOnly        whether this context is evaluated in a read-only scenario
 * @param soulContexts    hierarchical soul context stack (TenantSoul, OrgUnitSoul, UserSoul/AgentSoul)
 */
public record ImportanceContext(
    String text,
    float[] vector,
    RememberHints hints,
    SalienceProfile salienceProfile,
    MemoryType targetTier,
    float nearestDistance,
    double noveltyZScore,
    boolean readOnly,
    List<SoulContext> soulContexts
) {
    public ImportanceContext(String text, float[] vector, RememberHints hints,
                             SalienceProfile salienceProfile, MemoryType targetTier,
                             float nearestDistance, double noveltyZScore, boolean readOnly) {
        this(text, vector, hints, salienceProfile, targetTier, nearestDistance, noveltyZScore, readOnly, List.of());
    }

    public ImportanceContext {
        soulContexts = soulContexts != null ? List.copyOf(soulContexts) : List.of();
    }
    /**
     * Returns true if ICNU hints were provided by the caller.
     *
     * @return true if hints are present and not empty, false otherwise
     */
    public boolean hasHints() {
        return hints != null && !hints.isEmpty();
    }

    /**
     * Returns true if a salience profile with interests is available.
     *
     * @return true if a salience profile is present, false otherwise
     */
    public boolean hasSalienceProfile() {
        return salienceProfile != null;
    }
}
