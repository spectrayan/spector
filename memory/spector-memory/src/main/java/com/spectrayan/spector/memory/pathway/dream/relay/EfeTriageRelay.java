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
import com.spectrayan.spector.memory.model.SoulContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Stage 9 relay in {@link com.spectrayan.spector.memory.pathway.dream.DreamPathway}.
 *
 * <h3>Biological Analog: Prefrontal Executive Dream Evaluation via Multi-Soul Expected Free Energy</h3>
 * <p>Triages constructed dream scenarios into four canonical cognitive outcomes:
 * <b>EPISTEMIC</b> (high information gain/rule discovery), <b>PRAGMATIC</b> (goal-directed solution),
 * <b>IDENTITY</b> (self-model stabilization based on soul resonance), and <b>NOISE</b> (rejection with Hebbian inhibition).</p>
 *
 * @since 1.4.0
 */
public final class EfeTriageRelay implements SynapticRelay<DreamSignal> {

    private static final Logger log = LoggerFactory.getLogger(EfeTriageRelay.class);

    public static final float DEFAULT_EPISTEMIC_THRESHOLD = 0.70f;
    public static final float DEFAULT_PRAGMATIC_THRESHOLD = 0.50f;
    public static final float DEFAULT_IDENTITY_FALLBACK_THRESHOLD = 0.35f;

    private final float epistemicThreshold;
    private final float pragmaticThreshold;
    private final float identityFallbackThreshold;

    public EfeTriageRelay(float epistemicThreshold, float pragmaticThreshold, float identityFallbackThreshold) {
        this.epistemicThreshold = epistemicThreshold;
        this.pragmaticThreshold = pragmaticThreshold;
        this.identityFallbackThreshold = identityFallbackThreshold;
    }

    public EfeTriageRelay() {
        this(DEFAULT_EPISTEMIC_THRESHOLD, DEFAULT_PRAGMATIC_THRESHOLD, DEFAULT_IDENTITY_FALLBACK_THRESHOLD);
    }

    @Override
    public boolean transmit(final DreamSignal signal) {
        if (signal == null || signal.constructedScenes().isEmpty()) {
            return true;
        }

        List<DreamSignal.DreamScene> scenes = new ArrayList<>(signal.constructedScenes());
        signal.constructedScenes().clear();
        signal.survivingScenes().clear();

        SoulContext soul = signal.primarySoul();
        float identityResonanceThreshold = signal.config().identityResonanceThreshold();

        int epistemic = 0, pragmatic = 0, identity = 0, noise = 0;

        for (DreamSignal.DreamScene scene : scenes) {
            float q = scene.qualityScore();
            float[] vec = scene.embedding();

            float soulResonance = 0.0f;
            if (soul != null && soul.identityEmbedding() != null && vec != null && vec.length == soul.identityEmbedding().length && vec.length > 0) {
                soulResonance = AcceleratorRegistry.getSimilarityKernel().cosineSimilarity(vec, soul.identityEmbedding(), 1, vec.length)[0];
            }

            DreamSignal.TriageOutcome outcome;
            if (soulResonance >= identityResonanceThreshold) {
                // High direct alignment with the active soul self-model
                outcome = DreamSignal.TriageOutcome.IDENTITY;
                identity++;
            } else if (q >= epistemicThreshold) {
                outcome = DreamSignal.TriageOutcome.EPISTEMIC;
                epistemic++;
            } else if (q >= pragmaticThreshold) {
                outcome = DreamSignal.TriageOutcome.PRAGMATIC;
                pragmatic++;
            } else if (soul == null && q >= identityFallbackThreshold) {
                // Fallback for soul-less instances
                outcome = DreamSignal.TriageOutcome.IDENTITY;
                identity++;
            } else {
                outcome = DreamSignal.TriageOutcome.NOISE;
                noise++;
            }

            DreamSignal.DreamScene evaluated = new DreamSignal.DreamScene(
                    scene.id(),
                    scene.narrative(),
                    scene.insightText(),
                    scene.embedding(),
                    scene.sourceIds(),
                    q,
                    outcome
            );

            signal.addConstructedScene(evaluated);

            if (outcome != DreamSignal.TriageOutcome.NOISE) {
                signal.addSurvivingScene(evaluated);
            } else {
                signal.failedPairs().incrementAndGet();
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("EfeTriageRelay: triage complete — Epistemic: {}, Pragmatic: {}, Identity: {}, Noise: {} (Surviving: {}, Soul: {})",
                    epistemic, pragmatic, identity, noise, signal.survivingScenes().size(), soul != null ? soul.name() : "none");
        }

        return true;
    }

    @Override
    public String relayName() {
        return "efe_triage";
    }
}
