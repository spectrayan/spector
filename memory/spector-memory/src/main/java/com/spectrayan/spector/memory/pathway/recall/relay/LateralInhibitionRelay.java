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
package com.spectrayan.spector.memory.pathway.recall.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;
import com.spectrayan.spector.memory.model.ScoreBreakdown;
import com.spectrayan.spector.memory.pathway.RelayNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;

/**
 * Recall relay implementing Dentate-Gyrus Lateral Inhibition and Retrieval-Induced Forgetting arbitration (MR-04).
 *
 * <h3>Biological Analog: Dentate Gyrus Pattern Separation & GABAergic Lateral Inhibition</h3>
 * <p>Clusters overlapping candidate memories based on embedding cosine similarity (theta &gt;= 0.88).
 * Redundant clusters receive graded rank-ordered soft inhibition. Contradictory clusters (e.g. conflicting
 * facts, FLAG_CONTRADICTED) undergo multi-factor confidence arbitration, penalizing losers while preserving
 * full multi-evidence provenance and competitor telemetry.</p>
 */
public final class LateralInhibitionRelay implements SynapticRelay<RecallSignal> {

    private static final Logger log = LoggerFactory.getLogger(LateralInhibitionRelay.class);

    private final Function<String, float[]> vectorLookup;

    public LateralInhibitionRelay(Function<String, float[]> vectorLookup) {
        this.vectorLookup = vectorLookup;
    }

    @Override
    public boolean transmit(final RecallSignal signal) {
        if (signal == null) {
            return true;
        }

        List<CognitiveResult> candidates = signal.candidates();
        if (candidates == null || candidates.size() < 2) {
            return true;
        }

        RecallOptions options = signal.options();
        if (options == null || !options.enableLateralInhibition()) {
            return true;
        }

        int maxCandidates = Math.min(candidates.size(),
                Math.min(options.topK() * options.lateralInhibitionOverscanFactor(), options.lateralInhibitionMaxCandidates()));
        if (maxCandidates < 2) {
            return true;
        }

        float threshold = options.lateralInhibitionThreshold();
        float softKappa = options.lateralInhibitionSoftKappa();
        float hardKappa = options.lateralInhibitionHardKappa();

        // 1. Fetch vectors for top maxCandidates
        float[][] vectors = new float[maxCandidates][];
        boolean hasVectors = false;
        if (vectorLookup != null) {
            for (int i = 0; i < maxCandidates; i++) {
                vectors[i] = vectorLookup.apply(candidates.get(i).id());
                if (vectors[i] != null) {
                    hasVectors = true;
                }
            }
        }

        if (!hasVectors) {
            return true;
        }

        // 2. Single-linkage clustering based on cosine similarity >= threshold
        int[] clusterId = new int[maxCandidates];
        Arrays.fill(clusterId, -1);
        int nextCluster = 0;

        for (int i = 0; i < maxCandidates; i++) {
            if (vectors[i] == null) continue;
            for (int j = i + 1; j < maxCandidates; j++) {
                if (vectors[j] == null) continue;
                float sim = cosineSimilarity(vectors[i], vectors[j]);
                if (sim >= threshold) {
                    if (clusterId[i] == -1 && clusterId[j] == -1) {
                        clusterId[i] = nextCluster;
                        clusterId[j] = nextCluster;
                        nextCluster++;
                    } else if (clusterId[i] != -1 && clusterId[j] == -1) {
                        clusterId[j] = clusterId[i];
                    } else if (clusterId[i] == -1 && clusterId[j] != -1) {
                        clusterId[i] = clusterId[j];
                    } else if (clusterId[i] != clusterId[j]) {
                        // Merge clusters
                        int oldId = clusterId[j];
                        int newId = clusterId[i];
                        for (int k = 0; k < maxCandidates; k++) {
                            if (clusterId[k] == oldId) {
                                clusterId[k] = newId;
                            }
                        }
                    }
                }
            }
        }

        // Group members by cluster ID
        Map<Integer, List<Integer>> clusters = new HashMap<>();
        for (int i = 0; i < maxCandidates; i++) {
            if (clusterId[i] != -1) {
                clusters.computeIfAbsent(clusterId[i], k -> new ArrayList<>()).add(i);
            }
        }

        if (clusters.isEmpty()) {
            return true;
        }

        // 3. Process each multi-member cluster
        for (List<Integer> members : clusters.values()) {
            if (members.size() < 2) {
                continue;
            }

            List<String> allMemberIds = members.stream().map(idx -> candidates.get(idx).id()).toList();

            // Determine if cluster is CONTRADICTORY or REDUNDANT
            boolean isContradictory = false;
            for (int idx : members) {
                CognitiveResult r = candidates.get(idx);
                if (SynapticHeaderConstants.isContradicted(r.consolidationFlags())) {
                    isContradictory = true;
                    break;
                }
            }

            if (isContradictory) {
                // Contradiction arbitration: compute confidence C_i = 0.4*recency + 0.3*corroboration + 0.3*storageStrength
                float maxConfidence = 0.0001f;
                float[] confidences = new float[members.size()];
                for (int m = 0; m < members.size(); m++) {
                    int idx = members.get(m);
                    CognitiveResult r = candidates.get(idx);
                    float recency = Math.max(0.0f, 1.0f - (r.ageDays() / 365.0f));
                    float corroboration = Math.min(1.0f, (float) r.agentRecallCount() / 10.0f);
                    float storageStrength = Math.min(1.0f, r.importance() / 10.0f);
                    float conf = 0.4f * recency + 0.3f * corroboration + 0.3f * storageStrength;
                    confidences[m] = conf;
                    if (conf > maxConfidence) {
                        maxConfidence = conf;
                    }
                }

                for (int m = 0; m < members.size(); m++) {
                    int idx = members.get(m);
                    CognitiveResult r = candidates.get(idx);
                    float penalty = 1.0f - hardKappa * (1.0f - (confidences[m] / maxConfidence));
                    applyInhibition(candidates, idx, r, penalty, allMemberIds);
                }
            } else {
                // Redundant cluster: soft graded penalty based on initial rank
                for (int rankInCluster = 0; rankInCluster < members.size(); rankInCluster++) {
                    int idx = members.get(rankInCluster);
                    CognitiveResult r = candidates.get(idx);
                    // rank 0 (first member) -> penalty 1.0; rank 1 -> 1 - kappa*(1 - 1/2) = 1 - kappa*0.5
                    float rankFactor = 1.0f - (1.0f / (rankInCluster + 1));
                    float penalty = 1.0f - softKappa * rankFactor;
                    applyInhibition(candidates, idx, r, penalty, allMemberIds);
                }
            }
        }

        return true;
    }

    private static void applyInhibition(List<CognitiveResult> candidates, int idx, CognitiveResult r, float penalty, List<String> allMemberIds) {
        float newScore = r.score() * penalty;
        List<String> competitors = allMemberIds.stream().filter(id -> !id.equals(r.id())).toList();

        ScoreBreakdown oldBd = r.breakdown() != null ? r.breakdown() : ScoreBreakdown.NONE;
        ScoreBreakdown newBd = new ScoreBreakdown(
                oldBd.similarity(),
                oldBd.importanceDecay(),
                oldBd.tagBoostFactor(),
                oldBd.habituationPenalty(),
                oldBd.graphBoost(),
                oldBd.valenceAlignment(),
                newScore,
                oldBd.epistemicWeight(),
                oldBd.teleologicalWeight(),
                oldBd.pragmaticWeight(),
                oldBd.scoringRegime(),
                penalty,
                competitors
        );

        candidates.set(idx, r.withScoreAndBreakdown(newScore, newBd));
    }

    private static float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0.0f;
        }
        float dot = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA <= 0.0f || normB <= 0.0f) {
            return 0.0f;
        }
        return dot / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    @Override
    public String relayName() {
        return RelayNames.LATERAL_INHIBITION;
    }
}
