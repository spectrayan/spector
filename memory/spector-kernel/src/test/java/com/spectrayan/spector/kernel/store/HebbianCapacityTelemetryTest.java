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
package com.spectrayan.spector.kernel.store;

import com.spectrayan.spector.kernel.score.EdgeImportance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the capacity-exhaustion telemetry added in #983.
 *
 * <p>All three capacity boundaries used to lose data in silence. The node-index guard in
 * {@code strengthen} was the worst: it sits <b>before</b> the WAL append, so a refused association left no
 * trace in the graph, the log, metrics, or the write-ahead log. Because graph slots are allocated
 * monotonically and never reused, crossing the boundary once meant every later association in that
 * namespace was refused — permanently, while recall kept working and quietly degraded.</p>
 *
 * @see <a href="https://github.com/spectrayan/spector/issues/983">spectrayan/spector#983</a>
 */
@DisplayName("HebbianGraphMemory capacity telemetry (#983)")
class HebbianCapacityTelemetryTest {

    private static HebbianGraphMemory graph(int capacity, int maxDegree) {
        return new HebbianGraphMemory(capacity, capacity * maxDegree, maxDegree, EdgeImportance.DEFAULT);
    }

    @Nested
    @DisplayName("Node-index rejection")
    class NodeIndexRejection {

        @Test
        @DisplayName("an out-of-range node is counted, not silently dropped")
        void outOfRangeIsCounted() {
            HebbianGraphMemory g = graph(8, 4);

            assertThat(g.rejectedNodeOutOfRangeCount()).as("clean slate").isZero();

            g.strengthen(1, 2, 1.0f);
            assertThat(g.rejectedNodeOutOfRangeCount()).as("in-range pair is accepted").isZero();
            assertThat(g.degree(1)).isEqualTo(1);

            // Slot 8 is the 9th slot in a capacity-8 graph — exactly the situation a namespace reaches
            // once it has ingested `capacity` memories, since slots are never reused.
            g.strengthen(8, 1, 1.0f);

            assertThat(g.rejectedNodeOutOfRangeCount())
                    .as("refusal must be observable — this returned silently before #983")
                    .isEqualTo(1);
            assertThat(g.degree(1)).as("no edge was created").isEqualTo(1);
        }

        @Test
        @DisplayName("negative indexes are counted too")
        void negativeIsCounted() {
            HebbianGraphMemory g = graph(8, 4);
            g.strengthen(-1, 2, 1.0f);
            assertThat(g.rejectedNodeOutOfRangeCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("rejections accumulate, so sustained loss is distinguishable from a one-off")
        void rejectionsAccumulate() {
            HebbianGraphMemory g = graph(4, 4);
            for (int i = 4; i < 24; i++) {
                g.strengthen(i, 0, 1.0f);
            }
            assertThat(g.rejectedNodeOutOfRangeCount()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("Node-space headroom")
    class NodeSpaceHeadroom {

        @Test
        @DisplayName("headroom is reported and falls as slots are consumed")
        void headroomFallsAsSlotsAreUsed() {
            HebbianGraphMemory g = graph(100, 4);

            // Previously unobservable: every snapshot field was byte- or edge-denominated, and capacity
            // appeared only entangled inside allocatedBytes and csrOverflowOccupancy.
            assertThat(g.structureHealthSnapshot().nodeCapacity()).isEqualTo(100);
            assertThat(g.structureHealthSnapshot().nodeSpaceHeadroom())
                    .as("nothing seen yet")
                    .isEqualTo(1.0f);

            g.strengthen(0, 49, 1.0f);
            assertThat(g.structureHealthSnapshot().highestNodeIndexSeen()).isEqualTo(49);
            assertThat(g.structureHealthSnapshot().nodeSpaceHeadroom())
                    .as("50 of 100 slots addressed")
                    .isEqualTo(0.5f);
        }

        @Test
        @DisplayName("headroom reaches zero at exhaustion and does not go negative")
        void headroomFloorsAtZero() {
            HebbianGraphMemory g = graph(10, 4);
            g.strengthen(500, 0, 1.0f); // far beyond capacity

            var snap = g.structureHealthSnapshot();
            assertThat(snap.highestNodeIndexSeen()).isEqualTo(500);
            assertThat(snap.nodeSpaceHeadroom()).isEqualTo(0.0f);
        }

        @Test
        @DisplayName("the snapshot reports whether associations have been lost")
        void snapshotReportsLoss() {
            HebbianGraphMemory g = graph(8, 4);
            assertThat(g.structureHealthSnapshot().hasLostAssociations()).isFalse();

            g.strengthen(99, 1, 1.0f);
            assertThat(g.structureHealthSnapshot().hasLostAssociations())
                    .as("an operator needs one field to answer 'am I losing data?'")
                    .isTrue();
        }

        @Test
        @DisplayName("structures without a node dimension report NaN rather than a misleading zero")
        void notApplicableIsNaN() {
            var snap = new GraphStructureHealthSnapshot(
                    "entity-directory", 1024L, 512L, 0.5f, 0.25f, 1, Float.NaN, 0L, 0L);

            assertThat(snap.nodeCapacity())
                    .isEqualTo(GraphStructureHealthSnapshot.NODE_SPACE_NOT_APPLICABLE);
            assertThat(snap.nodeSpaceHeadroom()).isNaN();
            assertThat(snap.hasLostAssociations()).isFalse();
        }
    }

    @Nested
    @DisplayName("Degree cap")
    class DegreeCap {

        @Test
        @DisplayName("hitting the degree cap is counted as eviction, and the node stays within the cap")
        void degreeCapEvictionIsCounted() {
            int maxDegree = 4;
            HebbianGraphMemory g = graph(64, maxDegree);

            for (int neighbor = 1; neighbor <= maxDegree; neighbor++) {
                g.strengthen(0, neighbor, 1.0f);
            }
            assertThat(g.degreeCapEvictionCount()).as("still under the cap").isZero();
            assertThat(g.degree(0)).isEqualTo(maxDegree);

            // Beyond the cap this is importance-based eviction, not a blind drop: the new edge is kept only
            // if it outscores the weakest. Counted for visibility; the behaviour is deliberate.
            // Neighbour must be within node capacity, or the out-of-range guard fires first and this
            // measures the wrong boundary.
            g.strengthen(0, 50, 5.0f);

            assertThat(g.rejectedNodeOutOfRangeCount())
                    .as("this test must exercise the degree cap, not the node-index guard")
                    .isZero();

            assertThat(g.degreeCapEvictionCount()).isEqualTo(1);
            assertThat(g.degree(0))
                    .as("the cap is still respected after eviction")
                    .isLessThanOrEqualTo(maxDegree);
        }
    }

    @Nested
    @DisplayName("Default max degree")
    class DefaultMaxDegree {

        @Test
        @DisplayName("HebbianGraph default is 24, matching the Hebbian property rather than the entity cap")
        void defaultIsTwentyFour() {
            // spector-kernel takes no dependency on spector-config, so the cross-module equality with
            // SpectorPropertyConstants.DEFAULT_MEMORY_HEBBIAN_MAX_DEGREE is pinned in
            // GraphCapacityPlanTest. This pins the kernel's own half of that contract.
            assertThat(HebbianGraph.DEFAULT_MAX_DEGREE)
                    .as("24 is the Hebbian default; 16 is the entity graph's, which was copy-pasted into "
                            + "both cognitive builders as a fallback")
                    .isEqualTo(24);
        }
    }
}
