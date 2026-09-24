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
package com.spectrayan.spector.memory.bootstrap;

import com.spectrayan.spector.config.properties.MemoryProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the sizing of the namespace-global graph structures (#983).
 *
 * <p>The bug these tests exist to prevent: Hebbian capacity fell back to
 * {@code episodicPartitionCapacity} (default 1,000) to size a structure that spans every partition. Because
 * graph slots are allocated monotonically and never reused, the 1,001st memory ever ingested received no
 * associations — permanently, counting memories since deleted. Recall kept working and quietly degraded.</p>
 *
 * <p>No test previously set {@code hebbianGraphCapacity} or asserted anything about this sizing, which is
 * how a per-partition number came to size a global structure unnoticed.</p>
 *
 * @see <a href="https://github.com/spectrayan/spector/issues/983">spectrayan/spector#983</a>
 */
@DisplayName("GraphCapacityPlan — namespace-global graph sizing (#983)")
class GraphCapacityPlanTest {

    @Nested
    @DisplayName("Sizing source")
    class SizingSource {

        @Test
        @DisplayName("derives node capacity from total namespace capacity, NOT episodicPartitionCapacity")
        void derivesFromTotalCapacityNotPartitionCapacity() {
            MemoryProperties props = new MemoryProperties()
                    .capacity(250_000)
                    .episodicPartitionCapacity(1_000);

            GraphCapacityPlan plan = GraphCapacityPlan.from(props);

            // This assertion is the regression test for the bug. Before #983 it would have been 1_000.
            assertThat(plan.nodeCapacity())
                    .as("a namespace-global structure must not be sized from one partition")
                    .isEqualTo(250_000)
                    .isNotEqualTo(props.getEpisodicPartitionCapacity());
        }

        @Test
        @DisplayName("default capacity is 100,000 — two orders of magnitude above the old 1,000")
        void defaultIsTotalCapacityDefault() {
            GraphCapacityPlan plan = GraphCapacityPlan.from(new MemoryProperties());

            assertThat(plan.nodeCapacity()).isEqualTo(100_000);
        }

        @Test
        @DisplayName("changing episodicPartitionCapacity does not move graph capacity")
        void partitionCapacityIsIrrelevantToGraphSizing() {
            GraphCapacityPlan small = GraphCapacityPlan.from(
                    new MemoryProperties().capacity(50_000).episodicPartitionCapacity(16));
            GraphCapacityPlan large = GraphCapacityPlan.from(
                    new MemoryProperties().capacity(50_000).episodicPartitionCapacity(100_000));

            assertThat(small.nodeCapacity()).isEqualTo(large.nodeCapacity()).isEqualTo(50_000);
        }

        @Test
        @DisplayName("explicit hebbianGraphCapacity overrides the derived value")
        void explicitOverrideWins() {
            MemoryProperties props = new MemoryProperties()
                    .capacity(100_000)
                    .hebbianGraphCapacity(7_777);

            assertThat(GraphCapacityPlan.from(props).nodeCapacity()).isEqualTo(7_777);
        }

        @Test
        @DisplayName("a non-positive total capacity falls back rather than producing a zero-slot graph")
        void nonPositiveCapacityFallsBack() {
            // A zero-capacity graph would refuse every association — the exact failure mode being removed.
            MemoryProperties props = new MemoryProperties();
            props.setCapacity(0);

            assertThat(GraphCapacityPlan.from(props).nodeCapacity()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Temporal chain")
    class TemporalChain {

        @Test
        @DisplayName("defaults to the graph node capacity, not the partition capacity")
        void defaultsToNodeCapacity() {
            GraphCapacityPlan plan = GraphCapacityPlan.from(
                    new MemoryProperties().capacity(80_000).episodicPartitionCapacity(1_000));

            assertThat(plan.temporalCapacity()).isEqualTo(80_000);
        }

        @Test
        @DisplayName("explicit temporalChainCapacity overrides")
        void explicitOverrideWins() {
            GraphCapacityPlan plan = GraphCapacityPlan.from(
                    new MemoryProperties().capacity(80_000).temporalChainCapacity(1_234));

            assertThat(plan.temporalCapacity()).isEqualTo(1_234);
        }
    }

    @Nested
    @DisplayName("Max degree")
    class MaxDegree {

        @Test
        @DisplayName("effective default is 24, the Hebbian value — not the entity graph's 16")
        void defaultIsHebbianTwentyFour() {
            // Both builders previously hardcoded a 16 fallback, which is DEFAULT_MEMORY_ENTITY_MAX_DEGREE
            // (the entity graph's cap) copy-pasted. It never fired, because HebbianProperties.maxDegree
            // defaults to 24 and the `> 0` guard passes — so the effective default has always been 24.
            assertThat(GraphCapacityPlan.from(new MemoryProperties()).maxDegree())
                    .isEqualTo(GraphCapacityPlan.DEFAULT_MAX_DEGREE)
                    .isEqualTo(24);
        }

        @Test
        @DisplayName("the fallback constant tracks the Hebbian property default, not a restated literal")
        void fallbackTracksAuthoritativeConstant() {
            assertThat(GraphCapacityPlan.DEFAULT_MAX_DEGREE)
                    .isEqualTo(com.spectrayan.spector.config.SpectorPropertyConstants
                            .DEFAULT_MEMORY_HEBBIAN_MAX_DEGREE)
                    .isNotEqualTo(com.spectrayan.spector.config.SpectorPropertyConstants
                            .DEFAULT_MEMORY_ENTITY_MAX_DEGREE);
        }

        @Test
        @DisplayName("edge capacity is nodeCapacity * maxDegree")
        void edgeCapacityDerivesFromBoth() {
            MemoryProperties props = new MemoryProperties().capacity(1_000);
            props.getGraph().getHebbian().setMaxDegree(24);

            GraphCapacityPlan plan = GraphCapacityPlan.from(props);

            assertThat(plan.maxDegree()).isEqualTo(24);
            assertThat(plan.edgeCapacity()).isEqualTo(1_000 * 24);
        }
    }

    @Nested
    @DisplayName("Region sizing")
    class RegionSizing {

        @Test
        @DisplayName("region bytes are derived from the same plan the object uses")
        void regionBytesMatchPlan() {
            // The duplicated expression in CognitiveGraphBuilder and CognitiveCortexBuilder is why the
            // object and its on-disk region could disagree about capacity. Both now go through this plan.
            GraphCapacityPlan plan = GraphCapacityPlan.from(new MemoryProperties().capacity(1_000));

            long expectedHebbian = 64L + 16L
                    + (long) (plan.nodeCapacity() + 1) * Integer.BYTES
                    + (long) plan.nodeCapacity() * plan.maxDegree() * 12L;

            assertThat(plan.hebbianRegionBytes()).isEqualTo(expectedHebbian);
            assertThat(plan.temporalChainRegionBytes())
                    .isEqualTo(64L + 24L * plan.temporalCapacity());
        }

        @Test
        @DisplayName("region bytes grow with the raised default — the accepted footprint cost")
        void raisedDefaultCostsFootprint() {
            long oldStyle = GraphCapacityPlan.from(
                    new MemoryProperties().hebbianGraphCapacity(1_000)).hebbianRegionBytes();
            long newDefault = GraphCapacityPlan.from(new MemoryProperties()).hebbianRegionBytes();

            // Documents the tradeoff accepted in the spec. At the real default maxDegree of 24 this is
            // ~288KB -> ~27.8MB, not the ~19MB originally estimated from a mistaken maxDegree of 16.
            assertThat(oldStyle).isLessThan(1L * 1024 * 1024);
            assertThat(newDefault)
                    .isGreaterThan(25L * 1024 * 1024)
                    .isLessThan(30L * 1024 * 1024);
        }
    }
}
