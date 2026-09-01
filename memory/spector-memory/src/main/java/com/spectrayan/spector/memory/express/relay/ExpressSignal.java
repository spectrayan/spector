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
package com.spectrayan.spector.memory.express.relay;

import com.spectrayan.spector.memory.pathway.*;

import com.spectrayan.spector.memory.reflect.*;

import com.spectrayan.spector.memory.persist.*;

import com.spectrayan.spector.memory.bootstrap.*;

import com.spectrayan.spector.memory.api.*;

import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.aisme.homeostasis.InteroceptiveState;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.model.PersonaContext;
import com.spectrayan.spector.memory.model.SourceModality;

import com.spectrayan.spector.core.spacetime.ExpressTense;
import com.spectrayan.spector.core.spacetime.SpacetimeSimulationMode;
import com.spectrayan.spector.core.spacetime.Time2VecProjector;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

public record ExpressSignal(
        String queryText,
        List<CognitiveResult> candidates,
        InteroceptiveState interoceptiveState,
        SoulContext soulContext,
        PersonaContext personaContext,
        Set<SourceModality> requestedModalities,
        Map<String, Object> attributes,
        ExpressTense expressTense,
        long simulationTimeMs,
        float[] queryTau,
        SpacetimeSimulationMode spacetimeMode
) {

    public ExpressSignal(
            String queryText,
            List<CognitiveResult> candidates,
            InteroceptiveState interoceptiveState,
            SoulContext soulContext,
            PersonaContext personaContext,
            Set<SourceModality> requestedModalities,
            Map<String, Object> attributes) {
        this(
                queryText,
                candidates,
                interoceptiveState,
                soulContext,
                personaContext,
                requestedModalities,
                attributes,
                ExpressTense.FACT,
                System.currentTimeMillis(),
                Time2VecProjector.project(System.currentTimeMillis()),
                SpacetimeSimulationMode.EXPRESS_FACT
        );
    }

    public static Builder forQuery(String query, InteroceptiveState state, SoulContext soul) {
        return new Builder().queryText(query).interoceptiveState(state).soulContext(soul);
    }

    public static class Builder {
        private String queryText;
        private List<CognitiveResult> candidates = Collections.emptyList();
        private InteroceptiveState interoceptiveState;
        private SoulContext soulContext;
        private PersonaContext personaContext;
        private Set<SourceModality> requestedModalities = Collections.emptySet();
        private Map<String, Object> attributes = new HashMap<>();
        private ExpressTense expressTense = ExpressTense.FACT;
        private long simulationTimeMs = 0L;
        private float[] queryTau = null;
        private SpacetimeSimulationMode spacetimeMode = null;

        public Builder queryText(String queryText) {
            this.queryText = queryText;
            return this;
        }

        public Builder candidates(List<CognitiveResult> candidates) {
            this.candidates = candidates;
            return this;
        }

        public Builder interoceptiveState(InteroceptiveState interoceptiveState) {
            this.interoceptiveState = interoceptiveState;
            return this;
        }

        public Builder soulContext(SoulContext soulContext) {
            this.soulContext = soulContext;
            return this;
        }

        public Builder personaContext(PersonaContext personaContext) {
            this.personaContext = personaContext;
            return this;
        }

        public Builder requestedModalities(Set<SourceModality> requestedModalities) {
            this.requestedModalities = requestedModalities;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes;
            return this;
        }
        
        public Builder attribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }

        public Builder expressTense(ExpressTense tense) {
            this.expressTense = tense;
            return this;
        }

        public Builder simulationTimeMs(long simTime) {
            this.simulationTimeMs = simTime;
            return this;
        }

        public Builder queryTau(float[] tau) {
            this.queryTau = tau;
            return this;
        }

        public Builder spacetimeMode(SpacetimeSimulationMode mode) {
            this.spacetimeMode = mode;
            return this;
        }

        public ExpressSignal build() {
            final long simTime = simulationTimeMs > 0L ? simulationTimeMs : System.currentTimeMillis();
            final ExpressTense tense = expressTense != null ? expressTense : ExpressTense.FACT;
            final SpacetimeSimulationMode mode = spacetimeMode != null ? spacetimeMode : switch (tense) {
                case FACT -> SpacetimeSimulationMode.EXPRESS_FACT;
                case SIM -> SpacetimeSimulationMode.EXPRESS_SIM;
                case REPLAY -> SpacetimeSimulationMode.EXPRESS_REPLAY;
            };
            final float[] tau = queryTau != null ? queryTau : Time2VecProjector.project(simTime);

            return new ExpressSignal(
                    queryText,
                    candidates,
                    interoceptiveState,
                    soulContext,
                    personaContext,
                    requestedModalities,
                    attributes,
                    tense,
                    simTime,
                    tau,
                    mode
            );
        }
    }
}
