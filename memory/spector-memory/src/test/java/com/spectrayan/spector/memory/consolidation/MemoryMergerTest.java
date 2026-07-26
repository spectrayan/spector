/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.consolidation;

import com.spectrayan.spector.core.quantization.ScalarQuantizer;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;
import com.spectrayan.spector.memory.consolidation.MemoryMerger.MergedMemory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MemoryMergerTest {

    private ScalarQuantizer quantizer;

    @BeforeEach
    void setUp() {
        float[] mins = {0f, 0f, 0f, 0f};
        float[] maxs = {1f, 1f, 1f, 1f};
        quantizer = ScalarQuantizer.fromBounds(4, mins, maxs);
    }

    private CognitiveRecord createRecord(String text, float importance, long timestampMs, long synapticTags, byte valence, byte arousal, float storageStrength, byte[] vector) {
        return new CognitiveRecord(
                "mem-id", text, MemoryType.SEMANTIC, MemorySource.OBSERVED, new String[0],
                timestampMs, synapticTags, 1.0f, importance, 0, 0, (short) 0,
                valence, arousal, storageStrength, (byte) 0, (byte) 0,
                vector, -1, 0L, Map.of(), false
        );
    }

    private LlmProvider createMockLlm(String expectedResponse) {
        return new LlmProvider() {
            @Override
            public LlmResponse generate(LlmRequest request, GenerationOptions options) {
                return new LlmResponse(expectedResponse, 10, 10, "test-model");
            }
            @Override
            public String modelName() { return "test-model"; }
            @Override
            public boolean isAvailable() { return true; }
        };
    }

    private LlmProvider createThrowingLlm() {
        return new LlmProvider() {
            @Override
            public LlmResponse generate(LlmRequest request, GenerationOptions options) {
                throw new RuntimeException("LLM Failed");
            }
            @Override
            public String modelName() { return "test-model"; }
            @Override
            public boolean isAvailable() { return true; }
        };
    }

    private EmbeddingProvider createMockEmbedding(float[] vector) {
        return new EmbeddingProvider() {
            @Override
            public EmbeddingResult embed(String text) {
                return new EmbeddingResult(vector, 10, "test-emb");
            }
            @Override
            public int dimensions() { return vector.length; }
            @Override
            public String modelName() { return "test-emb"; }
        };
    }

    private EmbeddingProvider createThrowingEmbedding() {
        return new EmbeddingProvider() {
            @Override
            public EmbeddingResult embed(String text) {
                throw new RuntimeException("Embedding failed");
            }
            @Override
            public int dimensions() { return 4; }
            @Override
            public String modelName() { return "test-emb"; }
        };
    }

    @Test
    @DisplayName("LLM synthesizes new merged text successfully")
    void llmSynthesizesNewMergedText() {
        LlmProvider llm = createMockLlm("Merged by LLM");
        EmbeddingProvider emb = createMockEmbedding(new float[]{1f, 2f, 3f, 4f});

        MemoryMerger merger = new MemoryMerger(llm, emb);

        CognitiveRecord recA = createRecord("Text A", 5.0f, 1000L, 1L, (byte) 10, (byte) 20, 1.5f, new byte[]{1,2,3,4});
        CognitiveRecord recB = createRecord("Text B", 6.0f, 2000L, 2L, (byte) -10, (byte) 40, 2.5f, new byte[]{5,6,7,8});

        MergedMemory result = merger.merge(recA, recB, quantizer);
        
        assertThat(result.text()).isEqualTo("Merged by LLM");
        assertThat(result.vector()).containsExactly(1f, 2f, 3f, 4f);
    }

    @Test
    @DisplayName("Null LLM falls back to picking higher importance record")
    void nullLlmFallbackToHigherImportance() {
        MemoryMerger merger = new MemoryMerger(null, null);

        CognitiveRecord recA = createRecord("Lower importance text", 3.0f, 1000L, 0L, (byte) 0, (byte) 0, 1.0f, new byte[]{0,0,0,0});
        CognitiveRecord recB = createRecord("Higher importance text", 8.0f, 500L, 0L, (byte) 0, (byte) 0, 1.0f, new byte[]{10,10,10,10});

        MergedMemory result = merger.merge(recA, recB, quantizer);
        assertThat(result.text()).isEqualTo("Higher importance text");
    }

    @Test
    @DisplayName("Equal importance falls back to picking most recent timestamp")
    void equalImportanceFallbackToMostRecent() {
        MemoryMerger merger = new MemoryMerger(null, null);

        CognitiveRecord recA = createRecord("Older text", 5.0f, 1000L, 0L, (byte) 0, (byte) 0, 1.0f, new byte[]{0,0,0,0});
        CognitiveRecord recB = createRecord("Newer text", 5.0f, 2000L, 0L, (byte) 0, (byte) 0, 1.0f, new byte[]{10,10,10,10});

        MergedMemory result = merger.merge(recA, recB, quantizer);
        assertThat(result.text()).isEqualTo("Newer text");
    }

    @Test
    @DisplayName("LLM exception falls back to selection by importance")
    void llmFailureFallbackToSelection() {
        MemoryMerger merger = new MemoryMerger(createThrowingLlm(), null);

        CognitiveRecord recA = createRecord("A", 9.0f, 1000L, 0L, (byte) 0, (byte) 0, 1.0f, new byte[]{0,0,0,0});
        CognitiveRecord recB = createRecord("B", 2.0f, 2000L, 0L, (byte) 0, (byte) 0, 1.0f, new byte[]{0,0,0,0});

        MergedMemory result = merger.merge(recA, recB, quantizer);
        assertThat(result.text()).isEqualTo("A");
    }

    @Test
    @DisplayName("Null embedding falls back to decoded source vector of the selected record")
    void nullEmbeddingFallbackToSourceVector() {
        LlmProvider llm = createMockLlm("Merged text");
        // Embedding provider throws exception
        EmbeddingProvider emb = createThrowingEmbedding();

        MemoryMerger merger = new MemoryMerger(llm, emb);

        CognitiveRecord recA = createRecord("A", 9.0f, 1000L, 0L, (byte) 0, (byte) 0, 1.0f, new byte[]{10,10,10,10});
        CognitiveRecord recB = createRecord("B", 2.0f, 2000L, 0L, (byte) 0, (byte) 0, 1.0f, new byte[]{5,5,5,5});

        // Source vector will be taken from A because its text would have been selected in the fallback text logic
        MergedMemory result = merger.merge(recA, recB, quantizer);
        
        float expectedVal = 10 * (1/255f);
        assertThat(result.vector()).containsExactly(expectedVal, expectedVal, expectedVal, expectedVal);
    }

    @Test
    @DisplayName("Importance is maxed during merge")
    void metadataMergeMaxImportance() {
        MemoryMerger merger = new MemoryMerger(null, null);
        CognitiveRecord recA = createRecord("A", 2.0f, 1000L, 0L, (byte) 0, (byte) 0, 1.0f, new byte[]{0,0,0,0});
        CognitiveRecord recB = createRecord("B", 8.0f, 1000L, 0L, (byte) 0, (byte) 0, 1.0f, new byte[]{0,0,0,0});

        MergedMemory result = merger.merge(recA, recB, quantizer);
        assertThat(result.importance()).isEqualTo(8.0f);
    }

    @Test
    @DisplayName("Timestamp is maxed during merge")
    void metadataMergeMaxTimestamp() {
        MemoryMerger merger = new MemoryMerger(null, null);
        CognitiveRecord recA = createRecord("A", 5.0f, 3000L, 0L, (byte) 0, (byte) 0, 1.0f, new byte[]{0,0,0,0});
        CognitiveRecord recB = createRecord("B", 5.0f, 1000L, 0L, (byte) 0, (byte) 0, 1.0f, new byte[]{0,0,0,0});

        MergedMemory result = merger.merge(recA, recB, quantizer);
        assertThat(result.timestampMs()).isEqualTo(3000L);
    }

    @Test
    @DisplayName("Synaptic tags are bitwise ORed during merge")
    void metadataMergeSynapticTagsOr() {
        MemoryMerger merger = new MemoryMerger(null, null);
        // 0b1010 and 0b0101 -> 0b1111 (10 | 5 = 15)
        CognitiveRecord recA = createRecord("A", 5.0f, 1000L, 10L, (byte) 0, (byte) 0, 1.0f, new byte[]{0,0,0,0});
        CognitiveRecord recB = createRecord("B", 5.0f, 1000L, 5L, (byte) 0, (byte) 0, 1.0f, new byte[]{0,0,0,0});

        MergedMemory result = merger.merge(recA, recB, quantizer);
        assertThat(result.synapticTags()).isEqualTo(15L);
    }

    @Test
    @DisplayName("Valence is averaged during merge")
    void metadataMergeValenceAverage() {
        MemoryMerger merger = new MemoryMerger(null, null);
        CognitiveRecord recA = createRecord("A", 5.0f, 1000L, 0L, (byte) 50, (byte) 0, 1.0f, new byte[]{0,0,0,0});
        CognitiveRecord recB = createRecord("B", 5.0f, 1000L, 0L, (byte) -10, (byte) 0, 1.0f, new byte[]{0,0,0,0});

        MergedMemory result = merger.merge(recA, recB, quantizer);
        assertThat(result.valence()).isEqualTo((byte) 20);
    }

    @Test
    @DisplayName("Storage strength is maxed during merge")
    void metadataMergeStorageStrengthMax() {
        MemoryMerger merger = new MemoryMerger(null, null);
        CognitiveRecord recA = createRecord("A", 5.0f, 1000L, 0L, (byte) 0, (byte) 0, 4.5f, new byte[]{0,0,0,0});
        CognitiveRecord recB = createRecord("B", 5.0f, 1000L, 0L, (byte) 0, (byte) 0, 9.2f, new byte[]{0,0,0,0});

        MergedMemory result = merger.merge(recA, recB, quantizer);
        assertThat(result.storageStrength()).isEqualTo(9.2f);
    }

    @Test
    @DisplayName("Merges successfully when both LLM and Embedding providers are null")
    void bothProvidersNullStillMerges() {
        MemoryMerger merger = new MemoryMerger(null, null);
        CognitiveRecord recA = createRecord("A", 5.0f, 1000L, 0L, (byte) 0, (byte) 0, 4.5f, new byte[]{5,5,5,5});
        CognitiveRecord recB = createRecord("B", 5.0f, 2000L, 0L, (byte) 0, (byte) 0, 9.2f, new byte[]{10,10,10,10});

        // B has more recent timestamp, so it will be selected
        MergedMemory result = merger.merge(recA, recB, quantizer);
        
        assertThat(result.text()).isEqualTo("B");
        float expectedVal = 10 * (1/255f);
        assertThat(result.vector()).containsExactly(expectedVal, expectedVal, expectedVal, expectedVal);
    }
}
