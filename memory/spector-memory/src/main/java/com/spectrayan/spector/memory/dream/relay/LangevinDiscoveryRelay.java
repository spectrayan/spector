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
package com.spectrayan.spector.memory.dream.relay;

import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.memory.kernel.shape.DistributedMemoryTensor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Stage 8 relay in {@link com.spectrayan.spector.memory.DreamPathway}.
 *
 * <h3>Biological Analog: Langevin Stochastic Diffusion over Holographic Energy Landscape (Pribram Holonomic Brain)</h3>
 * <p>Executes continuous Langevin dynamics over the distributed holographic memory tensor:
 * \(\mathbf{v}_{t+1} = \mathbf{v}_t - \eta \nabla E(\mathbf{v}_t; \mathbf{T}) + \sqrt{2\eta\mathcal{T}}\boldsymbol{\epsilon}_t\)
 * to escape local episodic minima and discover interstitial unmapped concept basins.</p>
 *
 * @since 1.4.0
 */
public final class LangevinDiscoveryRelay implements SynapticRelay<DreamSignal> {

    private static final Logger log = LoggerFactory.getLogger(LangevinDiscoveryRelay.class);

    public static final float MIN_TEMPERATURE_FLOOR = 0.05f;
    public static final float FINITE_DIFFERENCE_STEP_H = 1e-3f;
    public static final float SEED_JITTER_SIGMA = 0.10f;
    public static final float DEFAULT_DISCOVERY_CONFIDENCE = 0.80f;
    public static final float DEFAULT_DISCOVERY_EFE = 0.20f;

    @Override
    public boolean transmit(final DreamSignal signal) {
        if (signal == null || signal.distributedMemoryTensor() == null) {
            return true;
        }

        DistributedMemoryTensor dmt = signal.distributedMemoryTensor();
        if (dmt.patternCount() == 0 && signal.seedVectors().isEmpty()) {
            return true;
        }

        int dim = dmt.inputDimension();
        float eta = signal.config().langevinStepSize();
        int steps = signal.config().langevinSteps();
        float temp = signal.temperature();
        float noveltyRadius = signal.config().noveltyRadius();
        float beta = 1.0f / Math.max(MIN_TEMPERATURE_FLOOR, temp);

        Random random = new Random(signal.startTime().toEpochMilli() + 999L);

        // Initialize state vector v0 from seed centroid or random unit vector
        float[] v = initializeVector(signal.seedVectors(), dim, random);

        // Langevin Stochastic Diffusion Iteration: v_{t+1} = v_t - eta * grad + sqrt(2 * eta * T) * epsilon
        float noiseScale = (float) Math.sqrt(2.0 * eta * temp);

        for (int step = 0; step < steps; step++) {
            float energy = dmt.evaluateEnergy(v, beta);
            if (Float.isInfinite(energy)) {
                // If tensor is zero, perform random exploratory walk
                for (int d = 0; d < dim; d++) {
                    v[d] += (float) (random.nextGaussian() * noiseScale);
                }
                continue;
            }

            // Estimate gradient via finite differences: grad_d = (E(v + h*e_d) - E(v - h*e_d)) / (2*h)
            float[] grad = new float[dim];
            for (int d = 0; d < dim; d++) {
                v[d] += FINITE_DIFFERENCE_STEP_H;
                float ePlus = dmt.evaluateEnergy(v, beta);
                v[d] -= 2 * FINITE_DIFFERENCE_STEP_H;
                float eMinus = dmt.evaluateEnergy(v, beta);
                v[d] += FINITE_DIFFERENCE_STEP_H;

                if (!Float.isInfinite(ePlus) && !Float.isInfinite(eMinus)) {
                    grad[d] = (ePlus - eMinus) / (2.0f * FINITE_DIFFERENCE_STEP_H);
                }
            }

            // SDE Update
            for (int d = 0; d < dim; d++) {
                v[d] = v[d] - (eta * grad[d]) + (float) (random.nextGaussian() * noiseScale);
            }
        }

        // Check if discovered state vector v is sufficiently distant (novel) from seed memories
        float minSeedDist = computeMinDistance(v, signal.seedVectors());

        if (minSeedDist >= noveltyRadius) {
            String id = UUID.randomUUID().toString();
            String insightText = String.format("Interstitial Concept Discovery via Langevin Dynamics (dist=%.3f, energy=%.3f)",
                    minSeedDist, dmt.evaluateEnergy(v, beta));

            ExtractedInsight insight = new ExtractedInsight(
                    id,
                    insightText,
                    v,
                    ExtractedInsight.InsightType.SEMANTIC,
                    new ArrayList<>(signal.seedMemoryIds()),
                    DEFAULT_DISCOVERY_CONFIDENCE,
                    DEFAULT_DISCOVERY_EFE
            );

            signal.addExtractedInsight(insight);

            if (log.isDebugEnabled()) {
                log.debug("LangevinDiscoveryRelay: discovered novel interstitial concept basin (dist={:.3f}, radius={:.3f})",
                        minSeedDist, noveltyRadius);
            }
        }

        return true;
    }

    private static float[] initializeVector(List<float[]> seeds, int dim, Random rng) {
        float[] v = new float[dim];
        if (seeds != null && !seeds.isEmpty()) {
            float[] first = seeds.get(0);
            for (int d = 0; d < Math.min(dim, first.length); d++) {
                v[d] = first[d] + (float) (rng.nextGaussian() * SEED_JITTER_SIGMA);
            }
        } else {
            for (int d = 0; d < dim; d++) {
                v[d] = (float) rng.nextGaussian();
            }
        }
        return v;
    }

    private static float computeMinDistance(float[] v, List<float[]> seeds) {
        if (seeds == null || seeds.isEmpty()) return 1.0f;
        float minDist = Float.MAX_VALUE;
        for (float[] s : seeds) {
            if (s == null) continue;
            float distSq = 0.0f;
            for (int d = 0; d < Math.min(v.length, s.length); d++) {
                float diff = v[d] - s[d];
                distSq += diff * diff;
            }
            float dist = (float) Math.sqrt(distSq);
            if (dist < minDist) {
                minDist = dist;
            }
        }
        return minDist != Float.MAX_VALUE ? minDist : 1.0f;
    }

    @Override
    public String relayName() {
        return "langevin_discovery";
    }
}
