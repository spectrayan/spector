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
package com.spectrayan.spector.memory.pathway.remember.relay;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.kernel.engram.EncodingHeader;
import com.spectrayan.spector.kernel.engram.field.EncodingHeaderFields;
import com.spectrayan.spector.kernel.store.SemanticMemory;
import com.spectrayan.spector.kernel.store.WorkingMemory;
import com.spectrayan.spector.memory.cortex.CognitiveMemoryRouter;
import com.spectrayan.spector.memory.pathway.RelayNames;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TombstoneGuardRelay} — content-level tombstone admission gating (#1019).
 */
class TombstoneGuardRelayTest {

    private static final int DIMS = 8;

    private SemanticMemory semanticStore;
    private WorkingMemory workingStore;
    private CognitiveMemoryRouter router;
    private ScalarQuantizer quantizer;

    @BeforeEach
    void setUp() {
        semanticStore = new SemanticMemory(DIMS, 100);
        workingStore = new WorkingMemory(DIMS, 100);
        router = new CognitiveMemoryRouter(workingStore, semanticStore, null, null);

        float[] mins = new float[DIMS];
        float[] maxs = new float[DIMS];
        java.util.Arrays.fill(maxs, 1.0f);
        quantizer = ScalarQuantizer.fromBounds(DIMS, mins, maxs);
    }

    @AfterEach
    void tearDown() {
        if (semanticStore != null) semanticStore.close();
        if (workingStore != null) workingStore.close();
    }

    // ── Core Blocking Scenarios ──

    @Test
    @DisplayName("Blocks re-ingestion when candidate vector matches a tombstoned record")
    void blocksReIngestionOfTombstonedContent() throws Exception {
        // Write a record, then tombstone it
        byte[] vectorBytes = quantizer.encode(new float[]{0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f});
        long offset = writeSemanticRecord(vectorBytes, false);
        semanticStore.tombstone(offset);

        // Attempt to re-ingest with same vector under a different ID
        TombstoneGuardRelay relay = new TombstoneGuardRelay(router, quantizer);
        RememberSignal signal = createSignal("new-id",
                new float[]{0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f}, MemoryType.SEMANTIC);

        boolean proceed = relay.transmit(signal);

        assertThat(proceed).isFalse();
        assertThat(signal.isTombstoneBlocked()).isTrue();
        assertThat(signal.isDuplicate()).isFalse(); // Distinct from ID-level dedup
    }

    @Test
    @DisplayName("Allows ingestion when content is genuinely different from tombstoned records")
    void allowsDifferentContentAfterTombstone() throws Exception {
        // Write and tombstone a record
        byte[] vectorBytes = quantizer.encode(new float[]{0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f});
        long offset = writeSemanticRecord(vectorBytes, false);
        semanticStore.tombstone(offset);

        // Attempt to ingest genuinely different content
        TombstoneGuardRelay relay = new TombstoneGuardRelay(router, quantizer);
        RememberSignal signal = createSignal("different-id",
                new float[]{0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f}, MemoryType.SEMANTIC);

        boolean proceed = relay.transmit(signal);

        assertThat(proceed).isTrue();
        assertThat(signal.isTombstoneBlocked()).isFalse();
    }

    @Test
    @DisplayName("Allows re-ingestion after vacuum/purge — tombstone data reclaimed")
    void allowsReIngestionAfterPurge() throws Exception {
        // Write, tombstone, then purge a record
        byte[] vectorBytes = quantizer.encode(new float[]{0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f});
        long offset = writeSemanticRecord(vectorBytes, false);
        semanticStore.tombstone(offset);
        semanticStore.purge(offset);

        // Attempt to re-ingest same vector — should be allowed since vector is zeroed
        TombstoneGuardRelay relay = new TombstoneGuardRelay(router, quantizer);
        RememberSignal signal = createSignal("re-ingest-id",
                new float[]{0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f}, MemoryType.SEMANTIC);

        boolean proceed = relay.transmit(signal);

        assertThat(proceed).isTrue();
        assertThat(signal.isTombstoneBlocked()).isFalse();
    }

    // ── Edge Cases ──

    @Test
    @DisplayName("Proceeds when no tombstoned records exist in the store")
    void proceedsWithNoTombstonedRecords() throws Exception {
        // Write a live record
        byte[] vectorBytes = quantizer.encode(new float[]{0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f});
        writeSemanticRecord(vectorBytes, false);

        TombstoneGuardRelay relay = new TombstoneGuardRelay(router, quantizer);
        RememberSignal signal = createSignal("new-id",
                new float[]{0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f}, MemoryType.SEMANTIC);

        boolean proceed = relay.transmit(signal);

        assertThat(proceed).isTrue();
    }

    @Test
    @DisplayName("Proceeds when store is empty")
    void proceedsOnEmptyStore() throws Exception {
        TombstoneGuardRelay relay = new TombstoneGuardRelay(router, quantizer);
        RememberSignal signal = createSignal("id",
                new float[]{0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f}, MemoryType.SEMANTIC);

        boolean proceed = relay.transmit(signal);

        assertThat(proceed).isTrue();
    }

    @Test
    @DisplayName("Proceeds when candidate has no vector")
    void proceedsWithNullVector() throws Exception {
        TombstoneGuardRelay relay = new TombstoneGuardRelay(router, quantizer);
        RememberSignal signal = createSignal("id", null, MemoryType.SEMANTIC);

        boolean proceed = relay.transmit(signal);

        assertThat(proceed).isTrue();
    }

    @Test
    @DisplayName("Custom distance threshold is respected")
    void customThresholdRespected() throws Exception {
        // Write and tombstone a record
        byte[] vectorBytes = quantizer.encode(new float[]{0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f});
        long offset = writeSemanticRecord(vectorBytes, false);
        semanticStore.tombstone(offset);

        // Candidate that is close but not identical
        float[] candidate = {0.52f, 0.52f, 0.52f, 0.52f, 0.52f, 0.52f, 0.52f, 0.52f};

        // Very strict threshold — should allow
        TombstoneGuardRelay strictRelay = new TombstoneGuardRelay(router, quantizer, 0.001f);
        RememberSignal strictSignal = createSignal("strict-id", candidate, MemoryType.SEMANTIC);
        assertThat(strictRelay.transmit(strictSignal)).isTrue();

        // Lenient threshold — should block
        TombstoneGuardRelay lenientRelay = new TombstoneGuardRelay(router, quantizer, 1.0f);
        RememberSignal lenientSignal = createSignal("lenient-id", candidate, MemoryType.SEMANTIC);
        assertThat(lenientRelay.transmit(lenientSignal)).isFalse();
        assertThat(lenientSignal.isTombstoneBlocked()).isTrue();
    }

    @Test
    @DisplayName("Only scans tombstoned records — live records are ignored")
    void ignoresLiveRecordsDuringTombstoneScan() throws Exception {
        // Write a live record with identical vector — should NOT trigger tombstone guard
        byte[] vectorBytes = quantizer.encode(new float[]{0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f});
        writeSemanticRecord(vectorBytes, false);

        TombstoneGuardRelay relay = new TombstoneGuardRelay(router, quantizer);
        RememberSignal signal = createSignal("new-id",
                new float[]{0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f}, MemoryType.SEMANTIC);

        boolean proceed = relay.transmit(signal);

        // Should proceed — live records are not tombstoned, dedup is DedupGuardRelay's job
        assertThat(proceed).isTrue();
        assertThat(signal.isTombstoneBlocked()).isFalse();
    }

    @Test
    @DisplayName("Relay name is correct")
    void relayNameIsCorrect() {
        TombstoneGuardRelay relay = new TombstoneGuardRelay(router, quantizer);
        assertThat(relay.relayName()).isEqualTo(RelayNames.TOMBSTONE_GUARD);
    }

    // ── Helpers ──

    private long writeSemanticRecord(byte[] quantizedVector, boolean tombstoned) {
        byte flags = tombstoned
                ? EncodingHeaderFields.withMemoryType(EncodingHeaderFields.FLAG_TOMBSTONE, MemoryType.SEMANTIC.ordinal())
                : EncodingHeaderFields.withMemoryType((byte) 0, MemoryType.SEMANTIC.ordinal());

        EncodingHeader header = new EncodingHeader(
                System.currentTimeMillis(),
                0L,    // synapticTags
                1.0f,  // norm
                5.0f,  // importance
                1,     // agentRecallCount
                (short) 0, // habituationCounter
                (byte) 0,  // valence
                flags,
                (byte) 0,  // arousal
                1.0f   // storageStrength
        );

        return semanticStore.write(header, quantizedVector);
    }

    private static RememberSignal createSignal(String id, float[] vector, MemoryType type) {
        return RememberSignal.forCognitive(
                id,
                "test content for " + id,
                vector,
                type,
                new String[]{"test"},
                MemorySource.OBSERVED,
                null,
                null,
                (short) 0
        );
    }
}
