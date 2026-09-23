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
import com.spectrayan.spector.config.properties.SkillProperties;
import com.spectrayan.spector.core.similarity.CosineSimilarity;
import com.spectrayan.spector.index.VectorIndex;
import com.spectrayan.spector.kernel.api.MemoryLocation;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.id.TsidGenerator;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.pathway.RelayNames;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deduplication relay performing cosine similarity comparison against active procedural skills (ADR-0086 §5.2, §5.7).
 *
 * <p>When a newly compiled skill candidate matches an existing procedural skill with cosine similarity
 * $\ge \text{duplicateCosine}$, this relay reroutes the candidate from {@link SkillSignal.Mode#COMPILE}
 * to {@link SkillSignal.Mode#REINFORCE} (near-duplicate slot conservation).</p>
 */
public final class SkillDedupRelay implements SynapticRelay<SkillSignal> {

    private static final Logger log = LoggerFactory.getLogger(SkillDedupRelay.class);
    private static final TsidGenerator TSID = new TsidGenerator();
    public static final float DEFAULT_DEDUP_THRESHOLD = 0.88f;

    private final Map<String, float[]> localSkillVectors = new ConcurrentHashMap<>();

    public SkillDedupRelay() {}

    /**
     * Registers a known procedural skill vector into the dedup index (useful for testing and sweep tracking).
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

        SkillProperties properties = signal.context() != null ? signal.context().find(SkillProperties.class).orElse(null) : null;
        float effectiveThreshold = (properties != null && properties.duplicateCosine() > 0.0f)
                ? properties.duplicateCosine()
                : DEFAULT_DEDUP_THRESHOLD;

        String mostSimilarId = null;
        float maxSimilarity = -1.0f;

        // Check local / registered / in-sweep skill vectors
        for (var entry : localSkillVectors.entrySet()) {
            float sim = CosineSimilarity.compute(candidateVector, entry.getValue());
            if (sim > maxSimilarity) {
                maxSimilarity = sim;
                mostSimilarId = entry.getKey();
            }
        }

        // If VectorIndex is bound in context, scan procedural vectors strictly
        VectorIndex index = signal.context() != null ? signal.context().find(VectorIndex.class).orElse(null) : null;
        MemoryIndex memoryIndex = signal.context() != null ? signal.context().find(MemoryIndex.class).orElse(null) : null;

        if (index != null) {
            try {
                var searchResults = index.search(candidateVector, 10);
                if (searchResults != null) {
                    for (var r : searchResults) {
                        String id = r.id();
                        if (!isProceduralSkill(id, memoryIndex)) {
                            continue; // Invariant: do not conflate semantic facts with procedural skills
                        }
                        float sim = r.score();
                        if (sim > maxSimilarity) {
                            maxSimilarity = sim;
                            mostSimilarId = id;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Vector index search in SkillDedupRelay skipped: {}", e.getMessage());
            }
        }

        if (maxSimilarity >= effectiveThreshold && mostSimilarId != null) {
            log.info("SkillDedupRelay: candidate matches existing skill '{}' (similarity={:.4f} >= {:.2f}); rerouting COMPILE -> REINFORCE",
                    mostSimilarId, maxSimilarity, effectiveThreshold);

            signal.duplicateOf(mostSimilarId);
            signal.persistedSkillId(mostSimilarId);
            signal.mode(SkillSignal.Mode.REINFORCE);
        } else {
            // Register this candidate vector so subsequent candidates in the same sweep detect it
            String candidateId = signal.persistedSkillId();
            if (candidateId == null) {
                candidateId = TSID.generate();
                signal.persistedSkillId(candidateId);
            }
            localSkillVectors.put(candidateId, candidateVector);
        }

        return true;
    }

    private boolean isProceduralSkill(final String id, final MemoryIndex memoryIndex) {
        if (id == null) return false;
        if (id.startsWith("skill-") || id.startsWith("proc-")) {
            return true;
        }
        if (memoryIndex != null) {
            MemoryLocation loc = memoryIndex.locate(id);
            if (loc != null) {
                return loc.type() == MemoryType.PROCEDURAL;
            }
        }
        return false;
    }

    @Override
    public String relayName() {
        return RelayNames.SKILL_DEDUP;
    }
}
