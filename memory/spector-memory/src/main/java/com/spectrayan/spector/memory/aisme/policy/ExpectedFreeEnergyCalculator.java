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
package com.spectrayan.spector.memory.aisme.policy;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.core.cognitive.ExpectedFreeEnergyKernel;
import com.spectrayan.spector.memory.model.AgentSoul;
import com.spectrayan.spector.memory.model.OrgUnitSoul;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.model.TenantSoul;
import com.spectrayan.spector.memory.model.UserSoul;

import java.util.Arrays;
import java.util.List;

/**
 * Evaluates candidate cognitive policies against a composite multi-soul prior preference distribution p(o).
 *
 * <h3>Biological Analog: Ventromedial Prefrontal Value Integration Circuit</h3>
 * <p>Composes prior preferences across the full soul hierarchy (AgentSoul, UserSoul, TenantSoul, OrgUnitSoul)
 * using configurable blending weights, then evaluates each policy's Expected Free Energy G(π)
 * decomposed into pragmatic risk (goal misalignment) and epistemic ambiguity (uncertainty).</p>
 */
public final class ExpectedFreeEnergyCalculator {

    private final List<SoulContext> soulContexts;
    private final float agentWeight;
    private final float userWeight;
    private final float tenantWeight;
    private final float orgUnitWeight;
    private final float epistemicWeight;
    private final float pragmaticWeight;

    /**
     * Constructs an EFE calculator with the active multi-soul context stack and configurable weights.
     *
     * @param soulContexts list of all active soul contexts for composite prior composition
     * @param agentWeight blending weight for AgentSoul identity embeddings
     * @param userWeight blending weight for UserSoul persona embeddings
     * @param tenantWeight blending weight for TenantSoul compliance embeddings
     * @param orgUnitWeight blending weight for OrgUnitSoul expertise embeddings
     * @param epistemicWeight weight for epistemic ambiguity term in G(π)
     * @param pragmaticWeight weight for pragmatic risk term in G(π)
     */
    public ExpectedFreeEnergyCalculator(
            List<SoulContext> soulContexts,
            float agentWeight, float userWeight,
            float tenantWeight, float orgUnitWeight,
            float epistemicWeight, float pragmaticWeight) {
        this.soulContexts = soulContexts != null ? List.copyOf(soulContexts) : List.of();
        this.agentWeight = agentWeight;
        this.userWeight = userWeight;
        this.tenantWeight = tenantWeight;
        this.orgUnitWeight = orgUnitWeight;
        this.epistemicWeight = epistemicWeight;
        this.pragmaticWeight = pragmaticWeight;
    }

    /**
     * Evaluates a single cognitive policy against the composite multi-soul prior preference.
     *
     * @param policy the candidate policy with predicted observation distributions
     * @param soulContexts override soul contexts (if null, uses constructor-provided contexts)
     * @param currentPosteriorMean current posterior mean from MentalStateTracker
     * @param currentPosteriorPrecision current posterior precision from MentalStateTracker
     * @return scored policy with decomposed G(π) components
     */
    public PolicyDecisionReport.ScoredPolicy evaluate(
            CognitivePolicy policy,
            List<SoulContext> soulContexts,
            float[] currentPosteriorMean,
            float[] currentPosteriorPrecision) {

        List<SoulContext> activeSouls = (soulContexts != null && !soulContexts.isEmpty())
                ? soulContexts : this.soulContexts;

        int dim = policy.predictedObservationMean().length;

        // Compose composite prior preference p(o) from weighted soul identity embeddings
        float[] compositePreference = new float[dim];
        float[] compositePrecision = new float[dim];
        Arrays.fill(compositePrecision, 1.0f); // Default unit precision

        for (SoulContext soul : activeSouls) {
            float weight = switch (soul) {
                case AgentSoul _ -> agentWeight;
                case UserSoul _ -> userWeight;
                case TenantSoul _ -> tenantWeight;
                case OrgUnitSoul _ -> orgUnitWeight;
            };
            float[] emb = soul.identityEmbedding();
            if (emb != null) {
                int copyLen = Math.min(dim, emb.length);
                for (int i = 0; i < copyLen; i++) {
                    compositePreference[i] += weight * emb[i];
                }
            }
        }

        // Compute G(π) = w_p * D_KL[q(o|π) || p(o)] + w_e * H(q(o|s,π))
        float pragmaticRisk = ExpectedFreeEnergyKernel.pragmaticRisk(
                policy.predictedObservationMean(), compositePreference,
                policy.predictedObservationPrecision(), compositePrecision);
        float epistemicGain = ExpectedFreeEnergyKernel.epistemicAmbiguity(
                policy.predictedObservationPrecision());

        float totalG = (pragmaticWeight * pragmaticRisk) + (epistemicWeight * epistemicGain);

        return new PolicyDecisionReport.ScoredPolicy(policy, pragmaticRisk, epistemicGain, totalG, 0.0f);
    }
}
