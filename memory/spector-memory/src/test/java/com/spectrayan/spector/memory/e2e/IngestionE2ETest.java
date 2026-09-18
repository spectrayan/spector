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
package com.spectrayan.spector.memory.e2e;

import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.memory.e2e.E2ESeedData.SeedMemory;

import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * E2E tests for seed data ingestion and index integrity.
 *
 * <p>Validates that all seed memories are correctly ingested, routed to the right
 * tier, and registered in the memory index.</p>
 */
@DisplayName("🧠 E2E: Seed Data Ingestion")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IngestionE2ETest extends AbstractE2ETest {

    @Test
    @Order(1)
    @DisplayName("Total memory count matches seed data size")
    void totalMemoryCount() {
        int total = memory.totalMemories();
        log.info("Total memories: {} (seed data: {})", total, seedMemories.size());

        assertThat(total)
                .as("Total memories should be >= seed data size")
                .isGreaterThanOrEqualTo(seedMemories.size());
    }

    @Test
    @Order(2)
    @DisplayName("Per-tier counts match seed data distribution")
    void perTierCounts() {
        Map<MemoryType, Long> expectedCounts = seedMemories.stream()
                .collect(Collectors.groupingBy(SeedMemory::type, Collectors.counting()));

        for (Map.Entry<MemoryType, Long> entry : expectedCounts.entrySet()) {
            int actual = memory.memoryCount(entry.getKey());
            log.info("  {} tier: expected≥{}, actual={}", entry.getKey(), entry.getValue(), actual);
            assertThat(actual)
                    .as(entry.getKey() + " tier count")
                    .isGreaterThanOrEqualTo(entry.getValue().intValue());
        }
    }

    @Test
    @Order(3)
    @DisplayName("Every seed memory ID is registered in MemoryIndex")
    void allIdsInIndex() {
        int missing = 0;
        for (SeedMemory seed : seedMemories) {
            var location = memory.admin().index().locate(seed.id());
            if (location == null) {
                log.warn("Missing from index: {}", seed.id());
                missing++;
            }
        }
        assertThat(missing)
                .as("All seed memory IDs should be in the index")
                .isEqualTo(0);
    }

    @Test
    @Order(4)
    @DisplayName("Spot-check known memory IDs are indexed with correct type")
    void spotCheckKnownIds() {
        // Verify a few well-known seed memory IDs across different tiers
        String[][] checks = {
                {"db-001", "EPISODIC"},
                {"db-005", "SEMANTIC"},
                {"proc-001", "PROCEDURAL"},
                {"entity-001", "EPISODIC"},
                {"arch-001", "EPISODIC"},
        };

        for (String[] check : checks) {
            var location = memory.admin().index().locate(check[0]);
            assertThat(location).as("Memory '%s' should be indexed", check[0]).isNotNull();
            assertThat(location.type().name())
                    .as("Memory '%s' should be in %s tier", check[0], check[1])
                    .isEqualTo(check[1]);
        }
    }

    @Test
    @Order(5)
    @DisplayName("Memory tier routing matches seed data type")
    void tierRoutingCorrect() {
        for (SeedMemory seed : seedMemories) {
            var location = memory.admin().index().locate(seed.id());
            assertThat(location).as("Location for " + seed.id()).isNotNull();
            assertThat(location.type())
                    .as("Tier routing for '%s' should match seed type %s", seed.id(), seed.type())
                    .isEqualTo(seed.type());
        }
    }
}
