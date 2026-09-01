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
package com.spectrayan.spector.memory.hebbian;

import com.spectrayan.spector.memory.synapse.AssociativePriorProvider;
import com.spectrayan.spector.memory.synapse.QueryAssociativeContext;

import java.util.List;
import java.util.function.LongFunction;

/**
 * Concrete {@link AssociativePriorProvider} backed by {@link CoActivationRecordMemory} (MR-06).
 *
 * <p>Combines STDP predictive strength from query context tags to candidate tags and log1p hub degree:
 * <pre>
 *   A_g = clamp(w_stdp * predictiveStrength + w_hub * log1p(degree), 0.0, 1.0)
 * </pre>
 * Hub dampening via log1p prevents runaway hub dominance.</p>
 */
public final class CoActivationAssociativePriorProvider implements AssociativePriorProvider {

    private final CoActivationRecordMemory coActivationMemory;
    private final float stdpWeight;
    private final float hubWeight;
    private final LongFunction<String[]> tagResolver;

    public CoActivationAssociativePriorProvider(
            CoActivationRecordMemory coActivationMemory,
            float stdpWeight,
            float hubWeight,
            LongFunction<String[]> tagResolver) {
        this.coActivationMemory = coActivationMemory;
        this.stdpWeight = stdpWeight;
        this.hubWeight = hubWeight;
        this.tagResolver = tagResolver;
    }

    @Override
    public float priorFor(long candidateOffset, long recordTags, QueryAssociativeContext ctx) {
        if (coActivationMemory == null || ctx == null) {
            return 0.0f;
        }

        String[] candidateTags = tagResolver != null ? tagResolver.apply(candidateOffset) : null;
        if (candidateTags == null || candidateTags.length == 0) {
            return 0.0f;
        }

        List<String> queryTags = ctx.queryTags();
        float stdpStrength = 0.0f;
        if (queryTags != null && !queryTags.isEmpty()) {
            stdpStrength = coActivationMemory.getPredictiveStrength(queryTags, candidateTags);
        }

        int totalDegree = 0;
        for (String tag : candidateTags) {
            int deg = coActivationMemory.getAssociatedTags(tag, 100).size();
            totalDegree += deg;
        }

        // log1p hub dampening (normalized with base scaling factor e.g. / 5.0)
        float hubSignal = (float) Math.log1p(totalDegree) / 5.0f;

        float unnormalized = stdpWeight * stdpStrength + hubWeight * hubSignal;
        return Math.clamp(unnormalized, 0.0f, 1.0f);
    }
}
