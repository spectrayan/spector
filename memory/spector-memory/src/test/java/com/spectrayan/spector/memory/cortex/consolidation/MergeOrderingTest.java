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
package com.spectrayan.spector.memory.cortex.consolidation;

import com.spectrayan.spector.kernel.api.MemorySource;
import com.spectrayan.spector.kernel.api.MemoryType;
import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.kernel.store.SegmentEngramRegion;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.pathway.remember.RememberPathway;
import com.spectrayan.spector.memory.sync.MemoryWal;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A duplicate merge retires the originals only after the merged memory is stored (#1168).
 */
class MergeOrderingTest {

    private BatchConsolidator consolidator;
    private RememberPathway rememberPathway;
    private SegmentEngramRegion store;
    private MemoryIndex index;
    private MemoryWal wal;
    private final CognitiveRecord recordA = record("dup-a", "Standup is at 9:30.", 0L);
    private final CognitiveRecord recordB = record("dup-b", "The standup starts at 9:30.", 512L);

    @BeforeEach
    void setUp() {
        LlmProvider llm = mock(LlmProvider.class);
        when(llm.generate(anyString(), any(GenerationOptions.class))).thenReturn("Standup starts at 9:30.");
        when(llm.isAvailable()).thenReturn(true);
        EmbeddingProvider embeddings = mock(EmbeddingProvider.class);
        when(embeddings.embed(anyString())).thenReturn(new EmbeddingResult(new float[] {1f, 0f, 0f, 0f}, 1, "mock"));
        consolidator = new BatchConsolidator(llm, embeddings);
        rememberPathway = mock(RememberPathway.class);
        store = mock(SegmentEngramRegion.class);
        index = mock(MemoryIndex.class);
        wal = mock(MemoryWal.class);
    }

    private static CognitiveRecord record(String id, String text, long offset) {
        return new CognitiveRecord(id, text, MemoryType.SEMANTIC, MemorySource.OBSERVED, new String[] {"standup"},
                1_700_000_000_000L, 0L, 1f, 1f, 0, 0, (short) 0, (byte) 0, (byte) 0, 1f, (byte) 0, (byte) 0,
                null, 0, offset, Map.of(), false);
    }

    private void stubIngest(boolean stored) {
        when(rememberPathway.ingestCognitiveWithHeader(anyString(), anyString(), any(), eq(MemoryType.SEMANTIC),
                any(), eq(MemorySource.REFLECTED), any())).thenReturn(stored);
    }

    private void merge() {
        consolidator.mergeDuplicate(recordA, recordB, null, store, mock(ScalarQuantizer.class),
                rememberPathway, index, wal);
    }

    @Test
    void rejectedIngestKeepsBothOriginals() {
        stubIngest(false);

        merge();

        verify(store, never()).tombstone(anyLong());
        verify(wal, never()).appendForget(anyString());
        verify(index, never()).retire(anyString());
    }

    @Test
    void storedMergeRetiresOriginalsAfterIngest() {
        stubIngest(true);

        merge();

        InOrder order = inOrder(rememberPathway, store, index);
        order.verify(rememberPathway).ingestCognitiveWithHeader(anyString(), anyString(), any(),
                eq(MemoryType.SEMANTIC), any(), eq(MemorySource.REFLECTED), any());
        order.verify(store).tombstone(0L);
        order.verify(index).retire("dup-a");
        verify(store).tombstone(512L);
        verify(index).retire("dup-b");
    }
}
