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
package com.spectrayan.spector.memory.pathway.express.relay;

import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.aisme.homeostasis.InteroceptiveState;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.model.PersonaContext;
import com.spectrayan.spector.kernel.api.SourceModality;

import com.spectrayan.spector.core.spacetime.ExpressTense;
import com.spectrayan.spector.core.spacetime.SpacetimeSimulationMode;
import com.spectrayan.spector.core.spacetime.Time2VecProjector;

import com.spectrayan.spector.commons.pathway.AbstractSignal;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ExpressSignal extends AbstractSignal {

    private final String queryText;
    private final List<CognitiveResult> candidates;
    private final InteroceptiveState interoceptiveState;
    private final SoulContext soulContext;
    private final PersonaContext personaContext;
    private final Set<SourceModality> requestedModalities;
    private final Map<String, Object> attributes;
    private final ExpressTense expressTense;
    private final long simulationTimeMs;
    private final float[] queryTau;
    private final SpacetimeSimulationMode spacetimeMode;

    public ExpressSignal(
            final String queryText,
            final List<CognitiveResult> candidates,
            final InteroceptiveState interoceptiveState,
            final SoulContext soulContext,
            final PersonaContext personaContext,
            final Set<SourceModality> requestedModalities,
            final Map<String, Object> attributes,
            final ExpressTense expressTense,
            final long simulationTimeMs,
            final float[] queryTau,
            final SpacetimeSimulationMode spacetimeMode) {
        this.queryText = queryText;
        this.candidates = candidates != null ? candidates : Collections.emptyList();
        this.interoceptiveState = interoceptiveState;
        this.soulContext = soulContext;
        this.personaContext = personaContext;
        this.requestedModalities = requestedModalities != null ? requestedModalities : Collections.emptySet();
        this.attributes = attributes != null ? attributes : new HashMap<>();
        this.expressTense = expressTense;
        this.simulationTimeMs = simulationTimeMs;
        this.queryTau = queryTau;
        this.spacetimeMode = spacetimeMode;
    }

    public ExpressSignal(
            final String queryText,
            final List<CognitiveResult> candidates,
            final InteroceptiveState interoceptiveState,
            final SoulContext soulContext,
            final PersonaContext personaContext,
            final Set<SourceModality> requestedModalities,
            final Map<String, Object> attributes) {
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

    public static Builder builder() {
        return new Builder();
    }

    public static Builder forQuery(final String query, final InteroceptiveState state, final SoulContext soul) {
        return new Builder().queryText(query).interoceptiveState(state).soulContext(soul);
    }

    public String queryText() {
        return queryText;
    }

    public List<CognitiveResult> candidates() {
        return candidates;
    }

    public InteroceptiveState interoceptiveState() {
        return interoceptiveState;
    }

    public SoulContext soulContext() {
        return soulContext;
    }

    public PersonaContext personaContext() {
        return personaContext;
    }

    public Set<SourceModality> requestedModalities() {
        return requestedModalities;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }

    public ExpressTense expressTense() {
        return expressTense;
    }

    public long simulationTimeMs() {
        return simulationTimeMs;
    }

    public float[] queryTau() {
        return queryTau;
    }

    public SpacetimeSimulationMode spacetimeMode() {
        return spacetimeMode;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpressSignal that)) return false;
        return simulationTimeMs == that.simulationTimeMs
                && Objects.equals(queryText, that.queryText)
                && Objects.equals(candidates, that.candidates)
                && Objects.equals(interoceptiveState, that.interoceptiveState)
                && Objects.equals(soulContext, that.soulContext)
                && Objects.equals(personaContext, that.personaContext)
                && Objects.equals(requestedModalities, that.requestedModalities)
                && Objects.equals(attributes, that.attributes)
                && expressTense == that.expressTense
                && Arrays.equals(queryTau, that.queryTau)
                && spacetimeMode == that.spacetimeMode;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(queryText, candidates, interoceptiveState, soulContext,
                personaContext, requestedModalities, attributes, expressTense, simulationTimeMs, spacetimeMode);
        result = 31 * result + Arrays.hashCode(queryTau);
        return result;
    }

    @Override
    public String toString() {
        return "ExpressSignal[" +
                "queryText=" + queryText +
                ", candidates=" + candidates +
                ", interoceptiveState=" + interoceptiveState +
                ", soulContext=" + soulContext +
                ", personaContext=" + personaContext +
                ", requestedModalities=" + requestedModalities +
                ", attributes=" + attributes +
                ", expressTense=" + expressTense +
                ", simulationTimeMs=" + simulationTimeMs +
                ", queryTau=" + Arrays.toString(queryTau) +
                ", spacetimeMode=" + spacetimeMode +
                ']';
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
