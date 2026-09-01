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
package com.spectrayan.spector.memory.aisme.lifespan;

import com.spectrayan.spector.core.similarity.LifespanThresholdKernel;
import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.aisme.lifespan.LifespanEvaluationResult.LifespanRetentionDecision;
import com.spectrayan.spector.memory.pathway.remember.relay.RememberSignal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Controller orchestrating dynamic lifespan-adaptive retention thresholds \(\tau(t)\)
 * and autobiographical memory tiering across multi-decade operational horizons.
 */
public final class LifespanRetentionController {

    private static final Logger log = LoggerFactory.getLogger(LifespanRetentionController.class);

    private final AismeConfig config;
    private final AtomicLong operationalEpoch = new AtomicLong(0L);
    private final AtomicLong activeVolumeSample = new AtomicLong(0L);

    /**
     * Constructs a LifespanRetentionController with the specified AISME configuration.
     *
     * @param config AISME configuration parameters
     */
    public LifespanRetentionController(final AismeConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.activeVolumeSample.set(config.lifespanVTarget());
        log.info("Initialized LifespanRetentionController: enabled={}, tau0={}, k={}, T0={}, V_target={}, gamma={}, flashbulbProtect={}",
                config.enableLifespan(), config.lifespanTau0(), config.lifespanK(),
                config.lifespanT0Epochs(), config.lifespanVTarget(), config.lifespanGamma(),
                config.lifespanFlashbulbProtect());
    }

    /**
     * Computes the current dynamic retention threshold \(\tau(t)\) based on the specified active memory volume.
     *
     * @param currentVolume active stored memory item count
     * @return evaluated \(\tau(t) \in [0.0, 1.0]\)
     */
    public float computeCurrentTau(final long currentVolume) {
        if (!config.enableLifespan()) {
            return config.lifespanTau0();
        }
        return LifespanThresholdKernel.compute(
                config.lifespanTau0(),
                config.lifespanK(),
                operationalEpoch.get(),
                config.lifespanT0Epochs(),
                currentVolume,
                config.lifespanVTarget(),
                config.lifespanGamma()
        );
    }

    /**
     * Evaluates the current retention threshold \(\tau(t)\) using the cached volume sample.
     *
     * @return current dynamic \(\tau(t)\)
     */
    public float currentTau() {
        return computeCurrentTau(activeVolumeSample.get());
    }

    /**
     * Increments the operational lifespan epoch counter (e.g. after each sleep reflection cycle).
     *
     * @return updated epoch count
     */
    public long advanceEpoch() {
        return operationalEpoch.incrementAndGet();
    }

    /**
     * Sets the operational lifespan epoch count directly.
     *
     * @param epoch non-negative epoch number
     */
    public void setEpoch(final long epoch) {
        if (epoch >= 0L) {
            operationalEpoch.set(epoch);
        }
    }

    /**
     * Returns the current operational lifespan epoch count.
     *
     * @return operational epoch count
     */
    public long getEpoch() {
        return operationalEpoch.get();
    }

    /**
     * Updates the current active volume metric.
     *
     * @param volume non-negative memory count
     */
    public void updateVolume(final long volume) {
        if (volume >= 0L) {
            activeVolumeSample.set(volume);
        }
    }

    /**
     * Returns the current sampled memory volume.
     *
     * @return sampled volume count
     */
    public long getVolume() {
        return activeVolumeSample.get();
    }

    /**
     * Evaluates an incoming {@link RememberSignal} against the lifespan retention model.
     *
     * @param signal incoming observation/memory signal
     * @return disposition decision and tier classification
     */
    public LifespanEvaluationResult evaluate(final RememberSignal signal) {
        if (signal == null) {
            return new LifespanEvaluationResult(
                    LifespanRetentionDecision.PRUNE,
                    LifespanTier.EPHEMERAL,
                    currentTau(),
                    0.0f,
                    false
            );
        }

        float importance = signal.importance() > 0.0f ? signal.importance() : 0.5f;
        boolean isFlashbulb = signal.isFlashbulb();
        String[] tags = signal.tags();

        return evaluate(importance, isFlashbulb, tags);
    }

    /**
     * Evaluates a memory record based on its importance, flashbulb state, and metadata tags.
     *
     * @param importance evaluated composite importance \(I(o_t)\)
     * @param isFlashbulb whether explicitly tagged as an amygdalar flashbulb
     * @param tags contextual tags
     * @return evaluation result
     */
    public LifespanEvaluationResult evaluate(
            final float importance,
            final boolean isFlashbulb,
            final String[] tags) {

        if (!config.enableLifespan()) {
            return new LifespanEvaluationResult(
                    LifespanRetentionDecision.RETAIN,
                    LifespanTier.FLAVOUR,
                    config.lifespanTau0(),
                    importance,
                    isFlashbulb
            );
        }

        // 1. Check for CORE tier (Milestones, Invariants, Flashbulbs)
        boolean hasCoreTag = false;
        if (tags != null) {
            for (String tag : tags) {
                if (tag != null) {
                    String t = tag.toLowerCase();
                    if (t.startsWith("milestone") || t.startsWith("soul") || t.startsWith("covenant")
                            || t.startsWith("core") || t.startsWith("identity") || t.equals("critical")) {
                        hasCoreTag = true;
                        break;
                    }
                }
            }
        }

        boolean isCore = isFlashbulb || importance >= config.importanceFlashbulbThreshold() || hasCoreTag;
        if (isCore && config.lifespanFlashbulbProtect()) {
            return new LifespanEvaluationResult(
                    LifespanRetentionDecision.RETAIN,
                    LifespanTier.CORE,
                    0.0f,
                    importance,
                    true
            );
        }

        float tau = currentTau();

        // 2. Check for FLAVOUR tier (Contextual memories 0.30 <= I < 0.85)
        if (importance >= 0.30f) {
            if (importance >= tau) {
                return new LifespanEvaluationResult(
                        LifespanRetentionDecision.RETAIN,
                        LifespanTier.FLAVOUR,
                        tau,
                        importance,
                        false
                );
            } else {
                return new LifespanEvaluationResult(
                        LifespanRetentionDecision.CONSOLIDATE,
                        LifespanTier.FLAVOUR,
                        tau,
                        importance,
                        false
                );
            }
        }

        // 3. EPHEMERAL tier (Routine observations I < 0.30)
        if (importance >= tau) {
            return new LifespanEvaluationResult(
                    LifespanRetentionDecision.RETAIN,
                    LifespanTier.EPHEMERAL,
                    tau,
                    importance,
                    false
            );
        } else {
            return new LifespanEvaluationResult(
                    LifespanRetentionDecision.PRUNE,
                    LifespanTier.EPHEMERAL,
                    tau,
                    importance,
                    false
            );
        }
    }
}
