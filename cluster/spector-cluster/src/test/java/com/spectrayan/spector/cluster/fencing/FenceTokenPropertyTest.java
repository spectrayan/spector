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
package com.spectrayan.spector.cluster.fencing;

import com.spectrayan.spector.cluster.store.InMemoryControlStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class FenceTokenPropertyTest {

    @Test
    @DisplayName("Property: Fence mismatch always refuses across random token generations (Req R10.6, ADR §15.8)")
    void testFenceMismatchAlwaysRefuses() {
        InMemoryControlStore store = new InMemoryControlStore();
        FenceTokenManager manager = new FenceTokenManager(store);

        Random random = new Random(42);
        String namespaceId = "ns-fencing-property";

        for (int i = 0; i < 1000; i++) {
            long currentEpoch = store.advanceNamespaceEpoch(namespaceId);
            manager.setLocalFence(namespaceId, currentEpoch);

            // Valid fence must succeed
            assertThat(manager.validateFence(namespaceId, String.valueOf(currentEpoch)))
                    .as("Matching active fence must be accepted")
                    .isTrue();

            // Stale fence must fail
            if (currentEpoch > 1) {
                long staleEpoch = currentEpoch - 1;
                assertThat(manager.validateFence(namespaceId, String.valueOf(staleEpoch)))
                        .as("Stale fence must be refused")
                        .isFalse();
            }

            // Arbitrary different fence must fail
            long randomDelta = 1 + random.nextInt(100);
            long mismatchEpoch = currentEpoch + randomDelta;
            assertThat(manager.validateFence(namespaceId, String.valueOf(mismatchEpoch)))
                    .as("Mismatched fence must be refused")
                    .isFalse();

            // Malformed / empty / null inputs must fail
            assertThat(manager.validateFence(namespaceId, null)).isFalse();
            assertThat(manager.validateFence(namespaceId, "")).isFalse();
            assertThat(manager.validateFence(namespaceId, "invalid-token")).isFalse();
        }

        // Rejection counter must be positive and track all failures (Req R9.2)
        assertThat(manager.getFenceRejectionCount()).isGreaterThan(2000L);
    }

    @Test
    @DisplayName("Property: Defect verification - removing fence check causes test to fail (Req R10.7)")
    void testDefectVerificationFenceValidation() {
        InMemoryControlStore store = new InMemoryControlStore();
        FenceTokenManager manager = new FenceTokenManager(store);

        manager.setLocalFence("ns-1", 10L);

        // Under normal logic:
        assertThat(manager.validateFence("ns-1", "9")).isFalse();
        assertThat(manager.validateFence("ns-1", "10")).isTrue();
        assertThat(manager.validateFence("ns-1", "11")).isFalse();
    }
}
