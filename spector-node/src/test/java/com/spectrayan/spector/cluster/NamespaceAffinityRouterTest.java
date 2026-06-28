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
package com.spectrayan.spector.cluster;

import com.spectrayan.spector.commons.error.SpectorValidationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link NamespaceAffinityRouter}.
 */
class NamespaceAffinityRouterTest {

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("valid parameters create router successfully")
        void validConstruction() {
            var router = new NamespaceAffinityRouter(0, 3);
            assertEquals(0, router.myOrdinal());
            assertEquals(3, router.podCount());
            assertTrue(router.ringSize() > 0);
        }

        @Test
        @DisplayName("rejects negative ordinal")
        void rejectsNegativeOrdinal() {
            assertThrows(SpectorValidationException.class,
                    () -> new NamespaceAffinityRouter(-1, 3));
        }

        @Test
        @DisplayName("rejects ordinal >= podCount")
        void rejectsOrdinalExceedingPodCount() {
            assertThrows(SpectorValidationException.class,
                    () -> new NamespaceAffinityRouter(3, 3));
        }

        @Test
        @DisplayName("rejects zero podCount")
        void rejectsZeroPodCount() {
            assertThrows(SpectorValidationException.class,
                    () -> new NamespaceAffinityRouter(0, 0));
        }

        @Test
        @DisplayName("rejects podCount exceeding max")
        void rejectsExcessivePodCount() {
            assertThrows(SpectorValidationException.class,
                    () -> new NamespaceAffinityRouter(0, 257));
        }

        @Test
        @DisplayName("single pod deployment is valid")
        void singlePodValid() {
            var router = new NamespaceAffinityRouter(0, 1);
            assertEquals(0, router.ownerOf("tenant", "user"));
            assertTrue(router.isLocal("tenant", "user"));
        }
    }

    @Nested
    @DisplayName("Routing Determinism")
    class RoutingTests {

        @Test
        @DisplayName("same namespace always maps to same pod")
        void deterministic() {
            var router = new NamespaceAffinityRouter(0, 3);
            int first = router.ownerOf("acme-corp", "user-alice");
            for (int i = 0; i < 100; i++) {
                assertEquals(first, router.ownerOf("acme-corp", "user-alice"),
                        "Routing must be deterministic");
            }
        }

        @Test
        @DisplayName("ownerOf returns value in [0, podCount)")
        void ownerInRange() {
            var router = new NamespaceAffinityRouter(0, 5);
            for (int i = 0; i < 1000; i++) {
                int owner = router.ownerOf("tenant-" + i, "user-" + i);
                assertTrue(owner >= 0 && owner < 5,
                        "Owner " + owner + " out of range [0, 5)");
            }
        }

        @Test
        @DisplayName("different namespaces distribute across pods")
        void distribution() {
            var router = new NamespaceAffinityRouter(0, 3);
            var counts = new HashMap<Integer, Integer>();

            for (int i = 0; i < 3000; i++) {
                int owner = router.ownerOf("tenant", "user-" + i);
                counts.merge(owner, 1, Integer::sum);
            }

            // With 3000 namespaces across 3 pods, each should get roughly 1000
            // Allow ±30% tolerance for hash distribution variance
            for (int ordinal = 0; ordinal < 3; ordinal++) {
                int count = counts.getOrDefault(ordinal, 0);
                assertTrue(count > 700 && count < 1300,
                        "Pod " + ordinal + " got " + count + " namespaces (expected ~1000)");
            }
        }

        @Test
        @DisplayName("isLocal correctly identifies ownership")
        void isLocalCorrectness() {
            // Create routers for each pod perspective
            var router0 = new NamespaceAffinityRouter(0, 3);
            var router1 = new NamespaceAffinityRouter(1, 3);
            var router2 = new NamespaceAffinityRouter(2, 3);

            // For any namespace, exactly one pod should report isLocal=true
            for (int i = 0; i < 100; i++) {
                String tenant = "t" + i;
                String ns = "u" + i;
                int localCount = 0;
                if (router0.isLocal(tenant, ns)) localCount++;
                if (router1.isLocal(tenant, ns)) localCount++;
                if (router2.isLocal(tenant, ns)) localCount++;
                assertEquals(1, localCount,
                        "Exactly one pod must own " + tenant + ":" + ns);
            }
        }

        @Test
        @DisplayName("all routers agree on ownership")
        void routersAgree() {
            var router0 = new NamespaceAffinityRouter(0, 3);
            var router1 = new NamespaceAffinityRouter(1, 3);

            for (int i = 0; i < 100; i++) {
                assertEquals(
                        router0.ownerOf("tenant", "user-" + i),
                        router1.ownerOf("tenant", "user-" + i),
                        "All routers must compute the same owner");
            }
        }
    }

    @Nested
    @DisplayName("Resize")
    class ResizeTests {

        @Test
        @DisplayName("resize changes podCount and ring")
        void resizeUpdatesPodCount() {
            var router = new NamespaceAffinityRouter(0, 3);
            assertEquals(3, router.podCount());

            router.resize(5);
            assertEquals(5, router.podCount());
        }

        @Test
        @DisplayName("resize preserves most namespace assignments (consistent hashing)")
        void resizeMinimalReassignment() {
            var router = new NamespaceAffinityRouter(0, 3);

            // Record initial assignments
            var before = new HashMap<String, Integer>();
            for (int i = 0; i < 1000; i++) {
                String key = "tenant:user-" + i;
                before.put(key, router.ownerOf("tenant", "user-" + i));
            }

            // Scale from 3 → 4 pods
            router.resize(4);

            int changed = 0;
            for (int i = 0; i < 1000; i++) {
                int after = router.ownerOf("tenant", "user-" + i);
                if (!before.get("tenant:user-" + i).equals(after)) {
                    changed++;
                }
            }

            // Consistent hashing: ~1/N keys should move (1/4 ≈ 25%)
            // Allow generous tolerance: 5-50% is acceptable
            double changeRate = (double) changed / 1000;
            assertTrue(changeRate < 0.50,
                    "Change rate " + changeRate + " too high (expected < 50%)");
            assertTrue(changeRate > 0.05,
                    "Change rate " + changeRate + " suspiciously low");
        }

        @Test
        @DisplayName("resize to same count is no-op")
        void resizeNoOp() {
            var router = new NamespaceAffinityRouter(0, 3);
            int ringBefore = router.ringSize();
            router.resize(3);
            assertEquals(ringBefore, router.ringSize());
        }

        @Test
        @DisplayName("resize rejects invalid podCount")
        void resizeRejectsInvalid() {
            var router = new NamespaceAffinityRouter(0, 3);
            assertThrows(SpectorValidationException.class, () -> router.resize(0));
            assertThrows(SpectorValidationException.class, () -> router.resize(257));
        }
    }

    @Nested
    @DisplayName("Observability")
    class ObservabilityTests {

        @Test
        @DisplayName("distribution sums to 1.0")
        void distributionSumsToOne() {
            var router = new NamespaceAffinityRouter(0, 3);
            Map<Integer, Double> dist = router.distribution();
            assertNotNull(dist);
            assertEquals(3, dist.size());

            double sum = dist.values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(1.0, sum, 0.001, "Distribution must sum to 1.0");
        }

        @Test
        @DisplayName("ring size equals podCount * virtualNodesPerPod")
        void ringSizeCorrect() {
            var router = new NamespaceAffinityRouter(0, 3, 100);
            assertEquals(300, router.ringSize());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("rejects null tenantId")
        void rejectsNullTenant() {
            var router = new NamespaceAffinityRouter(0, 3);
            assertThrows(NullPointerException.class,
                    () -> router.ownerOf(null, "user"));
        }

        @Test
        @DisplayName("rejects null namespaceId")
        void rejectsNullNamespace() {
            var router = new NamespaceAffinityRouter(0, 3);
            assertThrows(NullPointerException.class,
                    () -> router.ownerOf("tenant", null));
        }

        @Test
        @DisplayName("handles empty strings gracefully")
        void handlesEmptyStrings() {
            var router = new NamespaceAffinityRouter(0, 3);
            // Empty strings should be routed (they're valid keys)
            assertDoesNotThrow(() -> router.ownerOf("", ""));
        }

        @Test
        @DisplayName("handles very long keys")
        void handlesLongKeys() {
            var router = new NamespaceAffinityRouter(0, 3);
            String longKey = "x".repeat(10_000);
            assertDoesNotThrow(() -> router.ownerOf(longKey, longKey));
        }
    }
}
