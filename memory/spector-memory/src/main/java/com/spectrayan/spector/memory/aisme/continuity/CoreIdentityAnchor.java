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
package com.spectrayan.spector.memory.aisme.continuity;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.core.cognitive.NeuralManifoldDistance;
import com.spectrayan.spector.memory.aisme.fegr.GenerativeSelfModel;
import com.spectrayan.spector.memory.aisme.manifold.PersonalMetricTensor;

import java.util.Arrays;

/**
 * Immutable snapshot of an entity's core foundational identity state \(s_{\text{core}}\), serving as the
 * homeostatic Lyapunov attractor for long-horizon multi-decade continuity.
 *
 * <h3>Biological Analog: Ventromedial Prefrontal & Insular Core-Self Attractor</h3>
 * <p>Represents the foundational self-schema setpoint. While daily experiences induce synaptic plasticity,
 * the Soft Identity Anchor control law exerts an infinitesimal restoring force back toward this core attractor,
 * preventing catastrophic autobiographical drift over 50–150 year horizons.</p>
 */
public record CoreIdentityAnchor(
        float[] corePriorMean,
        long anchorTimestampMs,
        int snapshotVersion,
        int snapshotEpochs
) {

    /**
     * Compact constructor enforcing defensive copies and validation.
     */
    public CoreIdentityAnchor {
        if (corePriorMean == null || corePriorMean.length == 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Core prior mean must not be null or empty");
        }
        if (snapshotVersion < 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Snapshot version must be non-negative");
        }
        if (snapshotEpochs < 0) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "Snapshot epochs must be non-negative");
        }

        corePriorMean = Arrays.copyOf(corePriorMean, corePriorMean.length);
        if (anchorTimestampMs <= 0) {
            anchorTimestampMs = System.currentTimeMillis();
        }
    }

    /**
     * @return vector dimensionality of the core anchor
     */
    public int dimensions() {
        return corePriorMean.length;
    }

    /**
     * Creates an initial CoreIdentityAnchor from a {@link GenerativeSelfModel}.
     *
     * @param selfModel the active generative self model
     * @return initialized CoreIdentityAnchor
     */
    public static CoreIdentityAnchor fromGenerativeModel(GenerativeSelfModel selfModel) {
        if (selfModel == null) {
            throw new SpectorValidationException(ErrorCode.ARGUMENT_INVALID, "GenerativeSelfModel must not be null");
        }
        return new CoreIdentityAnchor(selfModel.priorMean(), System.currentTimeMillis(), 1, 0);
    }

    /**
     * Creates an initial CoreIdentityAnchor from a raw prior mean vector.
     *
     * @param priorMean foundational prior mean vector
     * @return initialized CoreIdentityAnchor
     */
    public static CoreIdentityAnchor fromPrior(float[] priorMean) {
        return new CoreIdentityAnchor(priorMean, System.currentTimeMillis(), 1, 0);
    }

    /**
     * Computes the Riemannian distance \(d_M(\boldsymbol{p}, \boldsymbol{p}_{\text{core}})\) on the cognitive manifold.
     *
     * @param currentPrior current generative prior mean vector
     * @param tensor active personal Riemannian metric tensor (nullable; falls back to Euclidean if null)
     * @return Riemannian distance to core anchor
     */
    public float computeManifoldDistance(float[] currentPrior, PersonalMetricTensor tensor) {
        if (currentPrior == null || currentPrior.length != dimensions()) {
            return Float.MAX_VALUE;
        }
        if (tensor != null && tensor.dimensions() == dimensions()) {
            return NeuralManifoldDistance.distance(
                    currentPrior,
                    corePriorMean,
                    tensor.diagonalScaling(),
                    tensor.lowRankComponents()
            );
        }
        // Euclidean fallback
        double sumSq = 0.0;
        for (int i = 0; i < dimensions(); i++) {
            float diff = currentPrior[i] - corePriorMean[i];
            sumSq += diff * diff;
        }
        return (float) Math.sqrt(sumSq);
    }

    /**
     * Evaluates the Longitudinal Continuity metric \(C(t, t+\Delta) = \exp(-\lambda \cdot d_M)\).
     *
     * @param currentPrior current generative prior mean vector
     * @param tensor active personal metric tensor
     * @param lambda decay sensitivity constant (\(\lambda > 0\))
     * @return continuity metric in \([0, 1]\)
     */
    public float computeContinuityScore(float[] currentPrior, PersonalMetricTensor tensor, float lambda) {
        float dM = computeManifoldDistance(currentPrior, tensor);
        if (Float.isInfinite(dM) || Float.isNaN(dM)) {
            return 0.0f;
        }
        float effectiveLambda = lambda > 0.0f ? lambda : 1.0f;
        return (float) Math.exp(-effectiveLambda * dM);
    }

    /**
     * Checks if the current generative prior lies within the Lyapunov attractor basin.
     *
     * @param currentPrior current generative prior mean vector
     * @param tensor active metric tensor
     * @param lyapunovThreshold maximum allowable distance \(\epsilon_{\text{Lyapunov}}\)
     * @return true if \(d_M \le \epsilon_{\text{Lyapunov}}\)
     */
    public boolean isWithinLyapunovBasin(float[] currentPrior, PersonalMetricTensor tensor, float lyapunovThreshold) {
        float dM = computeManifoldDistance(currentPrior, tensor);
        return dM <= lyapunovThreshold;
    }

    /**
     * Evolves the core anchor on deliberate life milestones / re-anchoring epochs.
     *
     * @param evolvedPrior new anchor baseline
     * @return new evolved CoreIdentityAnchor instance
     */
    public CoreIdentityAnchor withEvolvedAnchor(float[] evolvedPrior) {
        return new CoreIdentityAnchor(
                evolvedPrior,
                System.currentTimeMillis(),
                snapshotVersion + 1,
                snapshotEpochs + 1
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CoreIdentityAnchor that)) return false;
        return snapshotVersion == that.snapshotVersion &&
                snapshotEpochs == that.snapshotEpochs &&
                Arrays.equals(corePriorMean, that.corePriorMean);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(corePriorMean);
        result = 31 * result + snapshotVersion;
        result = 31 * result + snapshotEpochs;
        return result;
    }

    @Override
    public String toString() {
        return "CoreIdentityAnchor{" +
                "dim=" + corePriorMean.length +
                ", version=" + snapshotVersion +
                ", epochs=" + snapshotEpochs +
                '}';
    }
}
