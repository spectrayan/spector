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
package com.spectrayan.spector.memory.pathway;

import com.spectrayan.spector.commons.pathway.BulkheadRelay;
import com.spectrayan.spector.commons.pathway.CircuitBreakerRelay;
import com.spectrayan.spector.commons.pathway.ErrorPolicy;
import com.spectrayan.spector.commons.pathway.GatedRelay;
import com.spectrayan.spector.commons.pathway.PathwayComposer;
import com.spectrayan.spector.commons.pathway.PathwayEngine;
import com.spectrayan.spector.commons.pathway.NamedRelay;
import com.spectrayan.spector.commons.pathway.PathwayRecipe;
import com.spectrayan.spector.commons.pathway.RetryRelay;
import com.spectrayan.spector.commons.pathway.SynapticRelay;
import com.spectrayan.spector.commons.pathway.TimeoutRelay;
import com.spectrayan.spector.memory.pathway.decide.relay.DecideRecipe;
import com.spectrayan.spector.memory.pathway.decide.relay.DecideSignal;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamRecipe;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamSignal;
import com.spectrayan.spector.memory.pathway.express.relay.ExpressRecipe;
import com.spectrayan.spector.memory.pathway.express.relay.ExpressSignal;
import com.spectrayan.spector.memory.pathway.wander.relay.WanderRecipe;
import com.spectrayan.spector.memory.pathway.wander.relay.WanderSignal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shape gate for the four pathways that had no recipe until the ADR-0035 follow-up:
 * Dream, Wander, Express and Decide.
 *
 * <p>Remember, Recall and Reflect are covered by {@link PathwayParityTest.RecipeRelayParity},
 * which compares each recipe against its (deprecated) factory. These four never had a factory,
 * so there is nothing to compare against — the invariant is pinned against an explicit expected
 * table instead. Together the two gates cover all seven pathways.</p>
 *
 * <p>Dream additionally asserts decorator placement, because its ADR-0036 §14 row is the only
 * one with non-uniform policy: an {@code ABORT} gate, a budgeted LLM stage, and a nested-Remember
 * stage that must have a breaker and bulkhead but deliberately <em>no</em> timeout.</p>
 */
@DisplayName("Recipe shape parity (Dream / Wander / Express / Decide)")
class RecipeShapeParityTest {

    private static <S> PathwayEngine<S> compose(final String name, final PathwayRecipe<S> recipe) {
        final PathwayComposer<S> composer = PathwayComposer.of(name);
        recipe.compose(composer);
        return composer.build();
    }

    private static <S> List<ErrorPolicy> policiesOf(final PathwayEngine<S> engine) {
        return engine.entries().stream().map(PathwayEngine.RelayEntry::errorPolicy).toList();
    }

    /** Collects the decorator types wrapping a stage, outermost first, excluding the leaf relay. */
    private static <S> List<Class<?>> decoratorsOf(final PathwayEngine<S> engine, final String relayName) {
        final SynapticRelay<S> root = engine.entries().stream()
                .map(PathwayEngine.RelayEntry::relay)
                .filter(r -> relayName.equals(r.relayName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no stage named '" + relayName + "'"));

        final List<Class<?>> chain = new ArrayList<>();
        SynapticRelay<S> cursor = root;
        while (true) {
            final SynapticRelay<S> next;
            if (cursor instanceof NamedRelay<S> n) {
                // The composer re-wraps every stage in a NamedRelay to pin its trace name,
                // so this sits outside the decorators and is not itself one.
                cursor = n.delegate();
                continue;
            } else if (cursor instanceof GatedRelay<S> g) {
                next = g.delegate();
            } else if (cursor instanceof BulkheadRelay<S> b) {
                next = b.delegate();
            } else if (cursor instanceof CircuitBreakerRelay<S> c) {
                next = c.delegate();
            } else if (cursor instanceof TimeoutRelay<S> t) {
                next = t.delegate();
            } else if (cursor instanceof RetryRelay<S> r) {
                next = r.delegate();
            } else {
                return chain;
            }
            chain.add(cursor.getClass());
            cursor = next;
        }
    }

    /**
     * Guards {@link #decoratorsOf} itself: a helper that silently returns an empty chain would
     * make every {@code doesNotContain} assertion in this class pass for the wrong reason.
     */
    @Test
    @DisplayName("decorator walk actually unwraps — guards against vacuous doesNotContain")
    void decoratorWalkIsNotVacuous() {
        final PathwayEngine<DreamSignal> engine = compose("dream", new DreamRecipe());

        assertThat(decoratorsOf(engine, RelayNames.SCENE_CONSTRUCT))
                .as("scene_construct is the most-decorated stage; an empty chain means the "
                        + "walk failed to unwrap rather than that decorators are absent")
                .isNotEmpty();
        assertThat(decoratorsOf(engine, RelayNames.SALIENT_SEED))
                .as("a plain gated stage still yields its GatedRelay")
                .containsExactly(GatedRelay.class);
    }

    @Nested
    @DisplayName("Dream")
    class Dream {

        @Test
        @DisplayName("relay names and order match the ADR-0036 §14 Dream row")
        void dreamShape() {
            final PathwayEngine<DreamSignal> engine = compose("dream", new DreamRecipe());

            assertThat(engine.relayNames()).containsExactly(
                    RelayNames.DREAM_GATE,
                    RelayNames.SALIENT_SEED,
                    RelayNames.SPACETIME_SEED,
                    RelayNames.FRAGMENT_UNPACK,
                    RelayNames.HYPER_ASSOCIATE,
                    RelayNames.REM_REPLAY,
                    RelayNames.SCENE_CONSTRUCT,
                    RelayNames.COUNTERFACTUAL_PROBE,
                    RelayNames.LANGEVIN_DISCOVERY,
                    RelayNames.EFE_TRIAGE,
                    RelayNames.CONCEPT_EXTRACT,
                    RelayNames.DREAM_JOURNAL,
                    RelayNames.DREAM_INGESTION);
        }

        @Test
        @DisplayName("dream gate is ABORT; every other stage degrades")
        void dreamPolicies() {
            final PathwayEngine<DreamSignal> engine = compose("dream", new DreamRecipe());
            final List<ErrorPolicy> policies = policiesOf(engine);

            assertThat(policies.get(0))
                    .as("dream gate must ABORT — not dreaming is not a failure, and running the "
                            + "remaining stages against an empty signal wastes the sleep cycle")
                    .isEqualTo(ErrorPolicy.ABORT);
            assertThat(policies.subList(1, policies.size()))
                    .as("all stages after the gate degrade gracefully")
                    .containsOnly(ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        @Test
        @DisplayName("scene_construct is budgeted, breakered and bulkheaded (remote LLM)")
        void sceneConstructDecorators() {
            final PathwayEngine<DreamSignal> engine = compose("dream", new DreamRecipe());

            assertThat(decoratorsOf(engine, RelayNames.SCENE_CONSTRUCT))
                    .as("LLM stage needs a budget plus isolation")
                    .contains(GatedRelay.class, BulkheadRelay.class,
                            CircuitBreakerRelay.class, TimeoutRelay.class);
        }

        @Test
        @DisplayName("dream_ingestion has admission control but NO timeout (ADR-0036 §7.3)")
        void dreamIngestionHasNoTimeout() {
            final PathwayEngine<DreamSignal> engine = compose("dream", new DreamRecipe());
            final List<Class<?>> decorators = decoratorsOf(engine, RelayNames.DREAM_INGESTION);

            assertThat(decorators)
                    .as("nested Remember gets breaker + bulkhead")
                    .contains(BulkheadRelay.class, CircuitBreakerRelay.class);
            assertThat(decorators)
                    .as("nested Remember must NOT be budgeted: it ends in an mmap + WAL write "
                            + "that cannot observe an interrupt, so a timeout would report failure "
                            + "while the write completed on a detached thread")
                    .doesNotContain(TimeoutRelay.class);
        }
    }

    @Nested
    @DisplayName("Wander / Express / Decide — uniform DEGRADE, no decorators")
    class UniformDegrade {

        @Test
        @DisplayName("Wander relay names and order")
        void wanderShape() {
            final PathwayEngine<WanderSignal> engine = compose("wander_pathway", new WanderRecipe());

            assertThat(engine.relayNames()).containsExactly(
                    RelayNames.IDLE_GATE,
                    RelayNames.AUTOBIOGRAPHICAL_SAMPLING,
                    RelayNames.SPACETIME_SEED,
                    RelayNames.HOPFIELD_MIND_WANDERING,
                    RelayNames.MANIFOLD_SYNERGY,
                    RelayNames.HEBBIAN_REINFORCEMENT,
                    RelayNames.LONGITUDINAL_CONTINUITY);
            assertThat(policiesOf(engine)).containsOnly(ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        @Test
        @DisplayName("Express relay names and order")
        void expressShape() {
            final PathwayEngine<ExpressSignal> engine = compose("ExpressPathway", new ExpressRecipe());

            assertThat(engine.relayNames()).containsExactly(
                    RelayNames.IDIOLECT_STYLOMETRY,
                    RelayNames.VOCAL_PROSODY,
                    RelayNames.EMBODIED_KINESICS,
                    RelayNames.PHENOMENOLOGICAL_STREAM);
            assertThat(policiesOf(engine)).containsOnly(ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        @Test
        @DisplayName("Decide relay names and order")
        void decideShape() {
            final PathwayEngine<DecideSignal> engine = compose("decide_pathway", new DecideRecipe());

            assertThat(engine.relayNames()).containsExactly(
                    RelayNames.POLICY_INFERENCE,
                    RelayNames.EXPERIMENT_THOUGHT);
            assertThat(policiesOf(engine)).containsOnly(ErrorPolicy.DEGRADE_GRACEFULLY);
        }

        @Test
        @DisplayName("none of the three carries a resilience decorator — nothing remote to isolate")
        void noResilienceDecorators() {
            final PathwayEngine<WanderSignal> wander = compose("wander_pathway", new WanderRecipe());
            final PathwayEngine<ExpressSignal> express = compose("ExpressPathway", new ExpressRecipe());
            final PathwayEngine<DecideSignal> decide = compose("decide_pathway", new DecideRecipe());

            for (final String name : wander.relayNames()) {
                assertThat(decoratorsOf(wander, name))
                        .as("wander stage '%s'", name)
                        .doesNotContain(TimeoutRelay.class, CircuitBreakerRelay.class, BulkheadRelay.class);
            }
            for (final String name : express.relayNames()) {
                assertThat(decoratorsOf(express, name))
                        .as("express stage '%s'", name)
                        .doesNotContain(TimeoutRelay.class, CircuitBreakerRelay.class, BulkheadRelay.class);
            }
            for (final String name : decide.relayNames()) {
                assertThat(decoratorsOf(decide, name))
                        .as("decide stage '%s'", name)
                        .doesNotContain(TimeoutRelay.class, CircuitBreakerRelay.class, BulkheadRelay.class);
            }
        }
    }
}
