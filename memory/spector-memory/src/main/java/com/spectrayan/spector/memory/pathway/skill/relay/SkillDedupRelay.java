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
package com.spectrayan.spector.memory.pathway.skill.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.similarity.CosineSimilarity;
import com.spectrayan.spector.index.VectorIndex;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deduplication relay performing cosine similarity comparison against active procedural skills (ADR-0086 §5.2, §5.7).
 *
 * <p>When a newly compiled skill candidate matches an existing procedural skill with cosine similarity $\ge 0.88$,
 * this relay reroutes the candidate from {@link SkillSignal.Mode#COMPILE} to {@link SkillSignal.Mode#REINFORCE}
 * (Invariant #7: near-duplicate COMPILE becomes REINFORCE without allocating a duplicate slab slot).</p>
 */
public final class SkillDedupRelay implements SynapticRelay<SkillSignal> {

    private static final Logger log = LoggerFactory.getLogger(SkillDedupRelay.class);
    public static final float DEDUP_THRESHOLD = 0.88f;

    private final Map<String, float[]> localSkillVectors = new ConcurrentHashMap<>();

    public SkillDedupRelay() {}

    /**
     * Registers a known procedural skill vector into the dedup index (useful for isolated unit testing).
     */
    public void registerSkillVector(final String skillId, final float[] vector) {
        if (skillId != null && vector != null) {
            localSkillVectors.put(skillId, vector);
        }
    }

    @Override
    public boolean transmit(final SkillSignal signal) {
        if (signal.mode() == SkillSignal.Mode.REINFORCE || signal.extractedBody() == null) {
            return true;
        }

        float[] candidateVector = signal.vector();
        if (candidateVector == null) {
            EmbeddingProvider embedder = signal.context() != null ? signal.context().find(EmbeddingProvider.class).orElse(null) : null;
            if (embedder != null) {
                try {
                    candidateVector = embedder.embed(signal.extractedBody().body()).vector();
                    signal.vector(candidateVector);
                } catch (Exception e) {
                    log.debug("Embedding generation in SkillDedupRelay skipped: {}", e.getMessage());
                }
            }
        }

        if (candidateVector == null) {
            return true;
        }

        String mostSimilarId = null;
        float maxSimilarity = -1.0f;

        // Check local / registered skill vectors
        for (var entry : localSkillVectors.entrySet()) {
            float sim = CosineSimilarity.compute(candidateVector, entry.getValue());
            if (sim > maxSimilarity) {
                maxSimilarity = sim;
                mostSimilarId = entry.getKey();
            }
        }

        // If VectorIndex is bound in context, scan procedural vectors
        VectorIndex index = signal.context() != null ? signal.context().find(VectorIndex.class).orElse(null) : null;
        if (index != null) {
            try {
                var searchResults = index.search(candidateVector, 5);
                if (searchResults != null) {
                    for (var r : searchResults) {
                        float sim = r.score();
                        if (sim > maxSimilarity) {
                            maxSimilarity = sim;
                            mostSimilarId = r.id();
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Vector index search in SkillDedupRelay skipped: {}", e.getMessage());
            }
        }

        if (maxSimilarity >= DEDUP_THRESHOLD && mostSimilarId != null) {
            log.info("SkillDedupRelay: candidate matches existing skill '{}' (similarity={:.4f} >= {:.2f}); rerouting COMPILE -> REINFORCE",
                    mostSimilarId, maxSimilarity, DEDUP_THRESHOLD);

            signal.duplicateOf(mostSimilarId);
            signal.persistedSkillId(mostSimilarId);
            signal.mode(SkillSignal.Mode.REINFORCE);
        } else {
            // Register this candidate vector so subsequent candidates in the same session can dedup
            if (signal.persistedSkillId() != null) {
                localSkillVectors.put(signal.persistedSkillId(), candidateVector);
            }
        }

        return true;
    }

    @Override
    public String relayName() {
        return RelayNames.SKILL_DEDUP;
    }
}
