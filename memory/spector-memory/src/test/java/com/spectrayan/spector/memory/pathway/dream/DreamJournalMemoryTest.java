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
package com.spectrayan.spector.memory.pathway.dream;

import com.spectrayan.spector.kernel.api.DreamMode;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamSignal;
import com.spectrayan.spector.kernel.api.TriageOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DreamJournalMemoryTest {

    @Test
    void testTransientDreamJournalMemoryAppendAndCount() throws Exception {
        try (DreamJournalMemory journal = DreamJournalMemory.heap(10, 256)) {
            assertThat(journal.entryCount()).isEqualTo(0);

            DreamJournalMemory.DreamJournalEntry entry1 = new DreamJournalMemory.DreamJournalEntry(
                    "dream-1",
                    Instant.now(),
                    DreamMode.REM,
                    TriageOutcome.EPISTEMIC,
                    0.85f,
                    "Dream of cross-system synchronization",
                    "Synchronization reduces error rate",
                    List.of("mem-1", "mem-2")
            );

            journal.append(entry1);
            assertThat(journal.entryCount()).isEqualTo(1);

            DreamSignal.DreamScene scene = new DreamSignal.DreamScene(
                    "scene-2",
                    "Daydream of future plan",
                    "Planning speeds execution",
                    new float[]{0.1f, 0.2f},
                    List.of("mem-3"),
                    0.75f,
                    TriageOutcome.PRAGMATIC
            );

            journal.appendScene(scene);
            assertThat(journal.entryCount()).isEqualTo(2);

            List<DreamJournalMemory.DreamJournalEntry> entries = journal.readAll();
            assertThat(entries).hasSize(2);
            assertThat(entries.get(0).id()).isEqualTo("dream-1");
            assertThat(entries.get(0).narrativeText()).isEqualTo("Dream of cross-system synchronization");
            assertThat(entries.get(0).triageOutcome()).isEqualTo(TriageOutcome.EPISTEMIC);
            assertThat(entries.get(1).id()).isEqualTo("scene-2");
            assertThat(entries.get(1).narrativeText()).isEqualTo("Daydream of future plan");
        }
    }

    @Test
    void testPersistentDreamJournalMemory(@TempDir Path tempDir) throws Exception {
        Path journalFile = tempDir.resolve("dream-journal.dj");
        try (DreamJournalMemory journal = new DreamJournalMemory(journalFile, 50, 512)) {
            assertThat(journal.entryCount()).isEqualTo(0);

            DreamJournalMemory.DreamJournalEntry entry = new DreamJournalMemory.DreamJournalEntry(
                    "dream-p1",
                    Instant.now(),
                    DreamMode.THOUGHT_EXPERIMENT,
                    TriageOutcome.PRAGMATIC,
                    0.92f,
                    "Thought experiment on caching strategy",
                    "LRU with probabilistic bypass is optimal",
                    List.of("mem-10", "mem-20")
            );

            journal.append(entry);
            assertThat(journal.entryCount()).isEqualTo(1);

            List<DreamJournalMemory.DreamJournalEntry> recent = journal.readRecent(5);
            assertThat(recent).hasSize(1);
            assertThat(recent.get(0).id()).isEqualTo("dream-p1");
            assertThat(recent.get(0).mode()).isEqualTo(DreamMode.THOUGHT_EXPERIMENT);
            assertThat(recent.get(0).qualityScore()).isEqualTo(0.92f);
        }
    }
}
