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
package com.spectrayan.spector.memory.pipeline.gatherer;

import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index;
import com.spectrayan.spector.memory.cortex.MemoryBM25Index.BM25Candidate;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.cortex.PartitionRegistry;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.index.MemoryIndex;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.SourceModality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles candidate retrieval across vector (HNSW/SVASQ), sparse (SPLADE),
 * and keyword (BM25) search indexes.
 */
public class RecallCandidateGatherer {

    private static final Logger log = LoggerFactory.getLogger(RecallCandidateGatherer.class);

    private final MemoryIndex index;
    private final MemoryBM25Index bm25Index;

    public RecallCandidateGatherer(MemoryIndex index, MemoryBM25Index bm25Index) {
        this.index = index;
        this.bm25Index = bm25Index;
    }

    public MemoryIndex index() {
        return index;
    }

    public MemoryBM25Index bm25Index() {
        return bm25Index;
    }

    /**
     * Fuses BM25 text search candidates with existing vector recall results using Reciprocal Rank Fusion (RRF).
     */
    public void fuseBM25Candidates(List<CognitiveResult> vectorResults,
                                   List<BM25Candidate> bm25Hits,
                                   RecallOptions options,
                                   PartitionRegistry partitionRegistry) {
        if (bm25Hits == null || bm25Hits.isEmpty()) return;
        final int RRF_K = 60;

        Map<String, Integer> vectorRanks = new LinkedHashMap<>();
        for (int i = 0; i < vectorResults.size(); i++) {
            String id = vectorResults.get(i).id();
            if (id != null && !vectorRanks.containsKey(id)) {
                vectorRanks.put(id, i + 1);
            }
        }

        Map<String, Integer> bm25Ranks = new LinkedHashMap<>();
        for (int i = 0; i < bm25Hits.size(); i++) {
            String id = bm25Hits.get(i).id();
            if (id != null && !bm25Ranks.containsKey(id)) {
                bm25Ranks.put(id, i + 1);
            }
        }

        Set<String> allIds = new LinkedHashSet<>();
        allIds.addAll(vectorRanks.keySet());
        allIds.addAll(bm25Ranks.keySet());

        Map<String, Float> rrfScores = new HashMap<>();
        for (String id : allIds) {
            float score = 0f;
            Integer vr = vectorRanks.get(id);
            Integer br = bm25Ranks.get(id);
            if (vr != null) score += 1.0f / (RRF_K + vr);
            if (br != null) score += 1.0f / (RRF_K + br);
            rrfScores.put(id, score);
        }

        Map<String, CognitiveResult> existingById = new LinkedHashMap<>();
        for (CognitiveResult r : vectorResults) {
            if (r.id() != null && !existingById.containsKey(r.id())) {
                existingById.put(r.id(), r);
            }
        }

        vectorResults.clear();
        for (String id : allIds) {
            float rrfScore = rrfScores.get(id);
            CognitiveResult existing = existingById.get(id);

            if (existing != null) {
                vectorResults.add(new CognitiveResult(
                        existing.id(), existing.text(), rrfScore, existing.importance(),
                        existing.ageDays(), existing.agentRecallCount(), existing.valence(),
                        existing.memoryType(), existing.source(), existing.synapticTags(),
                        existing.decayFactor(), existing.ltpAdjustedDecay(),
                        existing.retrievalMode(), existing.breakdown(), existing.trace(),
                        existing.sourceModality(), existing.metadata()));
            } else if (index != null) {
                if (!options.includeContradictions() && partitionRegistry != null) {
                    MemoryIndex.MemoryLocation loc = index.locate(id);
                    if (loc != null) {
                        CognitiveMemoryRouter router = partitionRegistry.routerFor(loc.colocatedPartition());
                        if (router != null) {
                            MemorySegment segment = router.segmentFor(loc.type());
                            if (segment != null) {
                                CognitiveRecordLayout layout = router.layoutFor(loc.type());
                                byte cFlags = layout.readConsolidationFlags(segment, loc.offset());
                                if (SynapticHeaderConstants.isContradicted(cFlags)) continue;
                            }
                        }
                    }
                }

                String text = index.text(id);
                if (text == null || text.isEmpty()) continue;

                MemorySource source = index.source(id);
                String[] tags = index.tags(id);
                MemoryIndex.MemoryLocation loc = index.locate(id);
                MemoryType type = loc != null ? loc.type() : MemoryType.SEMANTIC;

                Map<String, String> bm25Meta = index.metadata(id);
                SourceModality bm25Modality = bm25Meta != null
                        ? SourceModality.fromName(bm25Meta.get(SourceModality.METADATA_KEY))
                        : SourceModality.TEXT;
                vectorResults.add(new CognitiveResult(
                        id, text, rrfScore, 0f, 0f,
                        (short) 0, (byte) 0, type, source,
                        tags, 1.0f, 1.0f, CognitiveResult.RetrievalMode.STANDARD, null, null,
                        bm25Modality, bm25Meta));
            }
        }

        vectorResults.sort(Comparator.comparing(CognitiveResult::score).reversed());

        log.debug("RRF fused {} vector + {} BM25 candidates -> {} unique results",
                vectorRanks.size(), bm25Ranks.size(), vectorResults.size());
    }
}
