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

import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.RecallOptions;

import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * E2E tests for prospective memory (scheduled reminders).
 *
 * <p>Validates that past-due reminders surface in recall results and
 * future reminders are correctly excluded.</p>
 */
@DisplayName("🧠 E2E: Prospective Memory (Reminders)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProspectiveMemoryE2ETest extends AbstractE2ETest {

    @Test
    @Order(1)
    @DisplayName("Past-due reminder appears in recall results")
    void pastDueReminderAppears() {
        memory.scheduleReminder(
                "Review the database migration V20 for customer_tier column",
                Instant.now().minusSeconds(60),
                "database", "migration");

        List<CognitiveResult> results = memory.recall("pending tasks",
                RecallOptions.builder().topK(15).build());

        boolean hasReminder = results.stream()
                .anyMatch(r -> r.text() != null && r.text().contains("customer_tier"));

        log.info("Past-due reminder found: {}", hasReminder);
        assertThat(hasReminder)
                .as("Past-due reminder should appear in recall results")
                .isTrue();
    }

    @Test
    @Order(2)
    @DisplayName("Future reminder does NOT appear in recall results")
    void futureReminderExcluded() {
        memory.scheduleReminder(
                "This reminder should NOT appear yet - scheduled far in the future",
                Instant.now().plusSeconds(3600),
                "future");

        List<CognitiveResult> results = memory.recall("future tasks",
                RecallOptions.builder().topK(15).build());

        boolean hasFutureReminder = results.stream()
                .anyMatch(r -> r.text() != null && r.text().contains("should NOT appear"));

        assertThat(hasFutureReminder)
                .as("Future reminder should NOT appear in recall")
                .isFalse();
    }

    @Test
    @Order(3)
    @DisplayName("Reminder with matching tags surfaces in tag-relevant query")
    void reminderWithTagsMatchesQuery() {
        memory.scheduleReminder(
                "Urgent: Check PostgreSQL replication lag before tomorrow's release",
                Instant.now().minusSeconds(30),
                "database", "postgresql", "urgent");

        List<CognitiveResult> results = memory.recall("PostgreSQL replication urgent",
                RecallOptions.builder().topK(10).build());

        boolean hasUrgent = results.stream()
                .anyMatch(r -> r.text() != null && r.text().contains("replication lag"));

        log.info("Urgent PostgreSQL reminder found: {}", hasUrgent);
        assertThat(hasUrgent)
                .as("Tagged reminder should surface for tag-relevant query")
                .isTrue();
    }
}
