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
package com.spectrayan.spector.cluster.ring;

import com.spectrayan.spector.cluster.routing.ConsistentHashRing;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ConsistentHashRing Mathematical Invariants and Properties (Req R3, R12)")
class ConsistentHashRingPropertyTest {

    @Test
    @DisplayName("Req R3.3, J2: Determinism across arbitrary member insertion orders")
    void determinismAcrossMemberPermutations() {
        List<String> baseMembers = List.of("node-alpha", "node-bravo", "node-charlie", "node-delta", "node-echo");

        // Build reference ring
        ConsistentHashRing refRing = ConsistentHashRing.of(1, baseMembers);

        List<RoutingKey> testKeys = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            testKeys.add(RoutingKey.ofTenanted("cell-1", "tenant-" + (i % 5), "namespace-" + i));
        }

        Map<RoutingKey, String> expectedAssignments = new HashMap<>();
        for (RoutingKey key : testKeys) {
            expectedAssignments.put(key, refRing.ownerOf(key));
        }

        // Test 20 randomized orderings of the same members
        Random random = new Random(42);
        for (int trial = 0; trial < 20; trial++) {
            List<String> shuffled = new ArrayList<>(baseMembers);
            Collections.shuffle(shuffled, random);

            ConsistentHashRing trialRing = ConsistentHashRing.of(1, shuffled);

            for (RoutingKey key : testKeys) {
                String assigned = trialRing.ownerOf(key);
                assertThat(assigned)
                        .withFailMessage("Trial %d: Assignment mismatch for key %s (expected %s, got %s)",
                                trial, key.keyMaterial(), expectedAssignments.get(key), assigned)
                        .isEqualTo(expectedAssignments.get(key));
            }
        }
    }

    @Test
    @DisplayName("Req R12.1: Exactly one owner assigned per key")
    void exactlyOneOwnerPerKey() {
        ConsistentHashRing ring = ConsistentHashRing.of(1, List.of("node-1", "node-2", "node-3"));

        for (int i = 0; i < 500; i++) {
            RoutingKey key = RoutingKey.ofTenanted("cell-1", "tenant-test", "ns-" + i);
            String owner = ring.ownerOf(key);

            assertThat(owner).isNotNull();
            assertThat(ring.members()).contains(owner);
        }
    }

    @Test
    @DisplayName("Req R3.4, R12.3: Churn bound on member addition and removal")
    void churnBoundOnTopologyChange() {
        List<String> initialMembers = List.of("node-1", "node-2", "node-3");
        ConsistentHashRing ringInitial = ConsistentHashRing.of(1, initialMembers);

        int keyCount = 10_000;
        List<RoutingKey> keys = new ArrayList<>(keyCount);
        for (int i = 0; i < keyCount; i++) {
            keys.add(RoutingKey.ofTenanted("cell-1", "tenant-" + (i % 10), "ns-" + i));
        }

        Map<RoutingKey, String> initialAssignments = new HashMap<>();
        for (RoutingKey key : keys) {
            initialAssignments.put(key, ringInitial.ownerOf(key));
        }

        // Add 4th member: expected churn ~ 1/4 (25%) of total keys
        List<String> fourMembers = List.of("node-1", "node-2", "node-3", "node-4");
        ConsistentHashRing ringFour = ConsistentHashRing.of(2, fourMembers);

        int movedKeys = 0;
        int movedToNewNode = 0;

        for (RoutingKey key : keys) {
            String initialOwner = initialAssignments.get(key);
            String newOwner = ringFour.ownerOf(key);

            if (!initialOwner.equals(newOwner)) {
                movedKeys++;
                if ("node-4".equals(newOwner)) {
                    movedToNewNode++;
                }
            }
        }

        double churnFraction = (double) movedKeys / keyCount;
        // With 160 vnodes, adding 4th node reassigns between 20% and 30% of keys
        assertThat(churnFraction).isBetween(0.20, 0.30);
        // All moved keys must move strictly to the newly added node (monotonicity property)
        assertThat(movedToNewNode).isEqualTo(movedKeys);
    }

    @Test
    @DisplayName("Req R3.5, R12.3: Balance factor across members (< 1.5x)")
    void balanceFactorAcrossMembers() {
        ConsistentHashRing ring = ConsistentHashRing.of(1, List.of("node-1", "node-2", "node-3", "node-4", "node-5"));

        int keyCount = 20_000;
        Map<String, Integer> counts = new HashMap<>();
        for (String m : ring.members()) {
            counts.put(m, 0);
        }

        for (int i = 0; i < keyCount; i++) {
            RoutingKey key = RoutingKey.ofTenanted("cell-1", "tenant-" + (i % 20), "ns-" + i);
            String owner = ring.ownerOf(key);
            counts.put(owner, counts.get(owner) + 1);
        }

        int min = counts.values().stream().min(Integer::compare).orElse(0);
        int max = counts.values().stream().max(Integer::compare).orElse(0);
        double ratio = (double) max / min;

        // Imbalance must be strictly less than 1.5x (ADR-0034 §15.7 alerts at 2x)
        assertThat(ratio)
                .withFailMessage("Imbalance ratio %.2f exceeds 1.5x (min=%d, max=%d)", ratio, min, max)
                .isLessThan(1.5);
    }

    @Test
    @DisplayName("Req R3.6: Empty member set fails loudly")
    void emptyMemberSetFailsLoudly() {
        assertThatThrownBy(() -> ConsistentHashRing.of(1, List.of()))
                .isInstanceOf(SpectorValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARGUMENT_INVALID);

        assertThatThrownBy(() -> ConsistentHashRing.of(1, null))
                .isInstanceOf(SpectorValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARGUMENT_INVALID);

        assertThatThrownBy(() -> ConsistentHashRing.of(1, List.of("   ", "")))
                .isInstanceOf(SpectorValidationException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ARGUMENT_INVALID);
    }
}
