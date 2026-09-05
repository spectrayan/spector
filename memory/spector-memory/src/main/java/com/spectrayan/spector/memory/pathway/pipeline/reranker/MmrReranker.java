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
package com.spectrayan.spector.memory.pathway.pipeline.reranker;

import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.cortex.PartitionRegistry;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.kernel.layout.FixedEngramLayout;
import com.spectrayan.spector.core.similarity.SimilarityFunction;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Maximal Marginal Relevance (MMR) reranker.
 */
public class MmrReranker {
    private final MemoryIndex index;
    private final PartitionRegistry partitionRegistry;
    private final float[] calibrationMins;
    private final float[] calibrationScales;

    public MmrReranker(MemoryIndex index, PartitionRegistry partitionRegistry, float[] calibrationMins, float[] calibrationScales) {
        this.index = index;
        this.partitionRegistry = partitionRegistry;
        this.calibrationMins = calibrationMins;
        this.calibrationScales = calibrationScales;
    }

    public List<CognitiveResult> rerank(List<CognitiveResult> candidates, float[] queryVector, float lambda, int topK) {
        if (candidates == null || candidates.isEmpty()) return candidates;
        
        List<CognitiveResult> S = new ArrayList<>();
        List<CognitiveResult> unselected = new ArrayList<>(candidates);
        
        int k = Math.min(topK, candidates.size());
        
        while (S.size() < k && !unselected.isEmpty()) {
            CognitiveResult best = null;
            float bestMmr = -Float.MAX_VALUE;
            int bestIdx = -1;
            
            for (int i = 0; i < unselected.size(); i++) {
                CognitiveResult di = unselected.get(i);
                float sim1 = di.score(); // original cognitive score
                
                float maxSim2 = 0f;
                if (!S.isEmpty()) {
                    float[] diVector = decodeVector(di.id());
                    if (diVector != null) {
                        for (CognitiveResult dj : S) {
                            float sim2 = computeSimilarity(diVector, dj.id());
                            if (sim2 > maxSim2) {
                                maxSim2 = sim2;
                            }
                        }
                    }
                }
                
                float mmr = lambda * sim1 - (1.0f - lambda) * maxSim2;
                if (mmr > bestMmr) {
                    bestMmr = mmr;
                    best = di;
                    bestIdx = i;
                }
            }
            
            if (best != null) {
                S.add(best);
                unselected.remove(bestIdx);
            }
        }
        
        return S;
    }
    
    private float[] decodeVector(String memoryId) {
        if (calibrationMins == null) return null;
        int length = calibrationMins.length;
        MemoryIndex.MemoryLocation loc = index.locate(memoryId);
        if (loc == null) return null;
        CognitiveMemoryRouter router = partitionRegistry.routerFor(loc.colocatedPartition());
        if (router == null) return null;
        MemorySegment seg = router.segmentFor(loc.type());
        if (seg == null) return null;
        FixedEngramLayout layout = router.layoutFor(loc.type());
        if (layout == null) return null;
        long offset = layout.vectorOffset(loc.offset());
        
        float[] vec = new float[length];
        for (int i = 0; i < length; i++) {
            int q = Byte.toUnsignedInt(seg.get(ValueLayout.JAVA_BYTE, offset + i));
            vec[i] = calibrationMins[i] + (q / 255.0f) * calibrationScales[i];
        }
        return vec;
    }
    
    private float computeSimilarity(float[] diVector, String memoryId) {
        MemoryIndex.MemoryLocation loc = index.locate(memoryId);
        if (loc == null) return 0f;
        CognitiveMemoryRouter router = partitionRegistry.routerFor(loc.colocatedPartition());
        if (router == null) return 0f;
        MemorySegment seg = router.segmentFor(loc.type());
        if (seg == null) return 0f;
        FixedEngramLayout layout = router.layoutFor(loc.type());
        if (layout == null) return 0f;
        long offset = layout.vectorOffset(loc.offset());
        
        float l2dist = SimilarityFunction.EUCLIDEAN.computeQuantizedFromSegment(
                diVector, seg, offset, calibrationMins, calibrationScales, calibrationMins.length);
        
        return 1.0f / (1.0f + l2dist);
    }
}
