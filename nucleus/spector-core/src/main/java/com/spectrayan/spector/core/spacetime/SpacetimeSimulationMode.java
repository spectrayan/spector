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
package com.spectrayan.spector.core.spacetime;

/**
 * Operating mode presets for Spacetime Simulation across Wander, Dream, and Express pathways (ADR-0031).
 *
 * <p>Configures harmonic in-phase weight (\(\rho_+\)), anti-phase weight (\(\rho_-\)), recency scaling (\(\lambda\)),
 * anti-phase candidate extraction, and future causal horizon gating.</p>
 *
 * @since 1.5.0
 */
public enum SpacetimeSimulationMode {

    /**
     * Spontaneous Default Mode Network (DMN) mind-wandering: balances in-phase and anti-phase exploration
     * with flattened recency decay (\(\lambda = 0.30\)) and prospective horizon enabled.
     */
    WANDER(0.35f, 0.35f, 0.30f, true, true),

    /**
     * NREM slow-wave sleep memory consolidation: focuses exclusively on in-phase harmonic replay of recent
     * high-salience traces with strict recency (\(\lambda = 1.00\)) and anti-phase disabled.
     */
    DREAM_NREM(0.10f, 0.00f, 1.00f, false, false),

    /**
     * REM sleep constructive recombination &amp; thought experiments: broad multi-seed juxtaposition
     * including anti-phase harmonics, flattened recency (\(\lambda = 0.30\)), and future horizon simulation.
     */
    DREAM_REM(0.35f, 0.35f, 0.30f, true, true),

    /**
     * Timeless prospection / unconstrained scenario simulation: completely removes age decay penalties
     * (\(\lambda = 0.00\)) while evaluating both in-phase and anti-phase harmonics.
     */
    TIMELESS_PROSPECTION(0.35f, 0.35f, 0.00f, true, true),

    /**
     * Factual waking verbalization: strict as-of-now clock, suppresses synthetic/dreamed memories.
     */
    EXPRESS_FACT(0.00f, 0.00f, 1.00f, false, false),

    /**
     * Simulated scenario verbalization: passes through dream/wander candidate seeds with simulation telemetry.
     */
    EXPRESS_SIM(0.00f, 0.00f, 0.30f, false, true),

    /**
     * Historical / counterfactual replay verbalization: evaluated against explicit replay timestamp.
     */
    EXPRESS_REPLAY(0.00f, 0.00f, 1.00f, false, true);

    private final float rhoPlus;
    private final float rhoMinus;
    private final float recencyLambda;
    private final boolean allowsAntiPhase;
    private final boolean allowsFuture;

    SpacetimeSimulationMode(
            final float rhoPlus,
            final float rhoMinus,
            final float recencyLambda,
            final boolean allowsAntiPhase,
            final boolean allowsFuture) {
        this.rhoPlus = rhoPlus;
        this.rhoMinus = rhoMinus;
        this.recencyLambda = recencyLambda;
        this.allowsAntiPhase = allowsAntiPhase;
        this.allowsFuture = allowsFuture;
    }

    /**
     * Weight multiplier for in-phase harmonic resonance (\(\psi > 0\)).
     */
    public float rhoPlus() {
        return rhoPlus;
    }

    /**
     * Weight multiplier for anti-phase harmonic divergence (\(\psi < 0\)).
     */
    public float rhoMinus() {
        return rhoMinus;
    }

    /**
     * Continuous scaling coefficient \(\lambda\) for mass-dilated logarithmic recency.
     */
    public float recencyLambda() {
        return recencyLambda;
    }

    /**
     * Whether this mode extracts anti-phase candidate seeds (most negative \(\psi\)).
     */
    public boolean allowsAntiPhase() {
        return allowsAntiPhase;
    }

    /**
     * Whether this mode admits memories timestamped after the query/simulation clock (\(t_i > t_s\)).
     */
    public boolean allowsFuture() {
        return allowsFuture;
    }
}
