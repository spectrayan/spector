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

import com.spectrayan.spector.kernel.layout.ProvenanceLayout;
import com.spectrayan.spector.kernel.layout.ProvenanceLayout.ProvenanceState;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multi-source targets, the source index and row tombstoning in {@link ProvenanceMemory} (#1144, #1145).
 */
class ProvenanceMemoryLineageTest {

    private static ProvenanceEdge memorySourceEdge(long target, long source, byte factIndex) {
        return new ProvenanceEdge(0L, target, (short) 1, factIndex, (byte) 2,
                0, 0, 0, 0, 0, (short) 0, (short) 0, 1_000L,
                ProvenanceLayout.SOURCE_SEMANTIC, ProvenanceLayout.TARGET_SEMANTIC, (byte) 0, source, 0);
    }

    private static ProvenanceEdge episodicEdge(long session, long target) {
        return new ProvenanceEdge(session, target, (short) 1, (byte) 0, (byte) 1,
                0, 1, 3, 0, 300, (short) 3, (short) 0, 1_000L);
    }

    @Test
    @DisplayName("A target with several source rows returns all of them; findByTarget returns the latest")
    void multiSourceTarget() {
        ProvenanceMemory mem = ProvenanceMemory.heap(16);
        mem.append(memorySourceEdge(100L, 1L, (byte) 0));
        mem.append(memorySourceEdge(100L, 2L, (byte) 1));

        List<ProvenanceState> rows = mem.findAllByTarget(100L);
        assertThat(rows).extracting(ProvenanceState::sourceTsid).containsExactly(1L, 2L);
        assertThat(mem.findByTarget(100L)).get().extracting(ProvenanceState::sourceTsid).isEqualTo(2L);
        assertThat(mem.targetCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Semantic-source rows are indexed by source, not by session 0")
    void sourceIndex() {
        ProvenanceMemory mem = ProvenanceMemory.heap(16);
        mem.append(memorySourceEdge(100L, 1L, (byte) 0));
        mem.append(memorySourceEdge(200L, 1L, (byte) 0));
        mem.append(episodicEdge(77L, 300L));

        assertThat(mem.findBySource(1L)).extracting(ProvenanceState::targetTsid).containsExactly(100L, 200L);
        assertThat(mem.findBySource(2L)).isEmpty();
        assertThat(mem.findBySession(0L)).isEmpty();
        assertThat(mem.findBySession(77L)).extracting(ProvenanceState::targetTsid).containsExactly(300L);
    }

    @Test
    @DisplayName("Tombstoning by source or target hides rows from every lookup and survives an index rebuild")
    void tombstoneRows() {
        ProvenanceMemory mem = ProvenanceMemory.heap(16);
        mem.append(memorySourceEdge(100L, 1L, (byte) 0));
        mem.append(memorySourceEdge(100L, 2L, (byte) 1));
        mem.append(episodicEdge(77L, 300L));

        assertThat(mem.tombstoneBySource(1L)).isEqualTo(1);
        assertThat(mem.tombstoneBySource(1L)).isZero();
        assertThat(mem.findBySource(1L)).isEmpty();
        assertThat(mem.findAllByTarget(100L)).extracting(ProvenanceState::sourceTsid).containsExactly(2L);

        assertThat(mem.tombstoneByTarget(300L)).isEqualTo(1);
        assertThat(mem.findByTarget(300L)).isEmpty();
        assertThat(mem.findBySession(77L)).isEmpty();
        assertThat(mem.replay()).extracting(ProvenanceState::targetTsid).containsExactly(100L);

        mem.rebuildIndexes();
        assertThat(mem.findBySource(1L)).isEmpty();
        assertThat(mem.findAllByTarget(100L)).hasSize(1);
        assertThat(mem.findByTarget(300L)).isEmpty();
    }
}
