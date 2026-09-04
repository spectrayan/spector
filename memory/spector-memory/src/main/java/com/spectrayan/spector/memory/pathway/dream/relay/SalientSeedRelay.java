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
package com.spectrayan.spector.memory.pathway.dream.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.core.spi.AcceleratorRegistry;
import com.spectrayan.spector.memory.persist.PartitionManager;
import com.spectrayan.spector.memory.cortex.EngramMemory;
import com.spectrayan.spector.memory.cortex.PartitionHandle;
import com.spectrayan.spector.memory.kernel.layout.CognitiveRecordLayout;
import com.spectrayan.spector.memory.kernel.layout.SynapticHeaderConstants;
import com.spectrayan.spector.memory.model.InterestDomain;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.SoulContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stage 2 relay in {@link com.spectrayan.spector.memory.pathway.dream.DreamPathway}.
 *
 * <h3>Biological Analog: Targeted Memory Reactivation (TMR) &amp; Soul-Salience Gating</h3>
 * <p>Scans autobiographical and episodic stores for salient memories, evaluating recency,
 * novelty, semantic alignment with the active {@link SoulContext}, and user {@link SalienceProfile}
 * interests via hardware-accelerated batch SIMD/GPU kernels.</p>
 *
 * @since 1.4.0
 */
public final class SalientSeedRelay implements SynapticRelay<DreamSignal> {

    private static final Logger log = LoggerFactory.getLogger(SalientSeedRelay.class);

    public static final double RECENCY_DECAY_PERIOD_SECONDS = 86400.0;
    public static final int CANDIDATE_POOL_MULTIPLIER = 4;
    public static final float SIMULATED_NOVELTY_ATTENUATION = 0.40f;

    private record SeedCandidate(String id, float[] vector, float salienceScore) {}

    @Override
    public boolean transmit(final DreamSignal signal) {
        if (signal == null) return false;

        // If seeds were already populated upstream (e.g. targeted thought experiments), keep them
        if (!signal.seedMemoryIds().isEmpty() && !signal.seedVectors().isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("SalientSeedRelay: utilizing {} pre-existing seeds", signal.seedMemoryIds().size());
            }
            return true;
        }

        PartitionManager pm = signal.partitionManager();
        if (pm == null) {
            return true;
        }

        List<PartitionHandle> handles = pm.snapshot();
        if (handles == null || handles.isEmpty()) {
            return true;
        }

        int maxSeeds = signal.config().maxDreamsPerCycle();
        int candidatePoolLimit = maxSeeds * CANDIDATE_POOL_MULTIPLIER;
        List<SeedCandidate> candidates = new ArrayList<>();

        SoulContext soul = signal.primarySoul();
        SalienceProfile salience = signal.salienceProfile();
        DreamConfig config = signal.config();

        for (PartitionHandle handle : handles) {
            if (handle.router() == null || candidates.size() >= candidatePoolLimit) {
                continue;
            }

            collectCandidates(handle.router().episodic(), candidates, candidatePoolLimit, "epi-" + handle.seq(), soul, salience, config);
            if (candidates.size() < candidatePoolLimit) {
                collectCandidates(handle.router().semantic(), candidates, candidatePoolLimit, "sem-" + handle.seq(), soul, salience, config);
            }
        }

        // Rank candidates descending by composite salience score
        candidates.sort(Comparator.comparingDouble(SeedCandidate::salienceScore).reversed());

        int selectedCount = Math.min(maxSeeds, candidates.size());
        for (int i = 0; i < selectedCount; i++) {
            SeedCandidate sc = candidates.get(i);
            signal.seedMemoryIds().add(sc.id());
            signal.seedVectors().add(sc.vector());
        }

        if (log.isDebugEnabled()) {
            log.debug("SalientSeedRelay: selected {} salient seeds for dream synthesis (soul={}, pool={})",
                    signal.seedMemoryIds().size(), soul != null ? soul.name() : "none", candidates.size());
        }

        return true;
    }

    private void collectCandidates(
            EngramMemory store,
            List<SeedCandidate> candidates,
            int limit,
            String prefix,
            SoulContext soul,
            SalienceProfile salience,
            DreamConfig config) {
        if (store == null || store.segment() == null) return;

        CognitiveRecordLayout layout = store.cognitiveLayout();
        MemorySegment segment = store.segment();
        int size = store.size();
        if (size <= 0) return;

        int vecBytes = layout.quantizedVecBytes();
        int dim = vecBytes;
        byte[] qBytes = new byte[vecBytes];

        float[] soulEmbedding = (soul != null && soul.identityEmbedding() != null && soul.identityEmbedding().length == dim)
                ? soul.identityEmbedding() : null;

        List<InterestDomain> interests = (salience != null && salience.interests() != null)
                ? salience.interests().stream().filter(in -> in != null && in.embedding() != null && in.embedding().length == dim).toList()
                : List.of();

        float wRecency = config.seedWeightRecency();
        float wNovelty = config.seedWeightNovelty();
        float wSoul = (soulEmbedding != null) ? config.seedWeightSoul() : 0.0f;
        float wSalience = (!interests.isEmpty()) ? config.seedWeightSalience() : 0.0f;

        float totalWeight = wRecency + wNovelty + wSoul + wSalience;
        if (totalWeight <= 0.0f) totalWeight = 1.0f;

        int stride = Math.max(1, size / limit);
        for (int i = 0; i < size && candidates.size() < limit; i += stride) {
            long offset = store.recordOffset(i);
            byte flags = layout.readFlags(segment, offset);
            if (SynapticHeaderConstants.isTombstoned(flags)) {
                continue;
            }

            // Read raw bytes and decode quantized vector
            MemorySegment.copy(segment, layout.vectorOffset(offset), MemorySegment.ofArray(qBytes), 0, vecBytes);
            float[] vector = new float[dim];
            for (int d = 0; d < dim; d++) {
                int byteIdx = d % vecBytes;
                vector[d] = (qBytes[byteIdx] & 0xFF) / 255.0f * 2.0f - 1.0f;
            }

            // Read metadata for composite salience score
            long epochSecs = layout.readTimestamp(segment, offset);
            boolean simulated = SynapticHeaderConstants.isSimulated(flags);
            boolean dreamed = SynapticHeaderConstants.isDreamed(flags);

            // 1. Recency
            float recencyScore = (float) Math.exp(-Math.max(0L, System.currentTimeMillis() / 1000L - epochSecs) / RECENCY_DECAY_PERIOD_SECONDS);

            // 2. Novelty (attenuated if already dreamed or simulated)
            float noveltyScore = (!simulated && !dreamed) ? 1.0f : SIMULATED_NOVELTY_ATTENUATION;

            // 3. Soul Identity Alignment via hardware-accelerated SPI CosineSimilarity
            float soulScore = 0.5f;
            if (soulEmbedding != null) {
                float cos = AcceleratorRegistry.getSimilarityKernel().cosineSimilarity(vector, soulEmbedding, 1, dim)[0];
                soulScore = Math.max(0.0f, (cos + 1.0f) / 2.0f);
            }

            // 4. Salience Profile Interests Semantic Match via SPI
            float salienceScore = 0.5f;
            if (!interests.isEmpty()) {
                float maxInterest = 0.0f;
                for (InterestDomain in : interests) {
                    float cos = AcceleratorRegistry.getSimilarityKernel().cosineSimilarity(vector, in.embedding(), 1, dim)[0];
                    float weighted = Math.max(0.0f, (cos + 1.0f) / 2.0f) * in.level().multiplier();
                    if (weighted > maxInterest) {
                        maxInterest = weighted;
                    }
                }
                salienceScore = Math.min(1.0f, maxInterest);
            }

            float compositeSalience = (wRecency * recencyScore + wNovelty * noveltyScore + wSoul * soulScore + wSalience * salienceScore) / totalWeight;

            candidates.add(new SeedCandidate(prefix + "-" + i, vector, compositeSalience));
        }
    }

    @Override
    public String relayName() {
        return "salient_seed";
    }
}
