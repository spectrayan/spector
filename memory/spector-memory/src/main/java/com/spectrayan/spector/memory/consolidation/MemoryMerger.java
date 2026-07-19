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
import com.spectrayan.spector.memory.model.CognitiveRecord;
import com.spectrayan.spector.memory.synapse.CognitiveRecordLayout;
import com.spectrayan.spector.memory.synapse.SynapticHeaderConstants;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles merging of complementary duplicate memories.
 */
public final class MemoryMerger {

    private static final Logger log = LoggerFactory.getLogger(MemoryMerger.class);

    private final LlmProvider textGenerator;
    private final EmbeddingProvider embeddingProvider;

    public MemoryMerger(LlmProvider textGenerator, EmbeddingProvider embeddingProvider) {
        this.textGenerator = textGenerator;
        this.embeddingProvider = embeddingProvider;
    }

    public record MergedMemory(
            String text,
            float[] vector,
            float importance,
            long timestampMs,
            long synapticTags,
            byte valence,
            byte arousal,
            float storageStrength
    ) {}

    /**
     * Merges two cognitive records into a single consolidated representation.
     */
    public MergedMemory merge(CognitiveRecord recordA, CognitiveRecord recordB, ScalarQuantizer quantizer) {
        // 1. Text merge
        String mergedText = null;
        if (textGenerator != null) {
            try {
                String prompt = String.format(
                        "Merge these two duplicate/related statements into a single, cohesive, concise factual statement.\n\n" +
                        "Statement 1: \"%s\"\n" +
                        "Statement 2: \"%s\"\n\n" +
                        "Merged statement:", recordA.text(), recordB.text());

                String response = textGenerator.generate(prompt, GenerationOptions.CONCISE);
                if (response != null && !response.isBlank()) {
                    mergedText = response.trim();
                }
            } catch (Exception e) {
                log.warn("MemoryMerger: LLM synthesis failed, falling back to selection: {}", e.getMessage());
            }
        }

        boolean usedLlm = (mergedText != null);
        if (!usedLlm) {
            // Fallback: pick the higher importance, or the more recent one if equal
            if (recordA.importance() > recordB.importance()) {
                mergedText = recordA.text();
            } else if (recordB.importance() > recordA.importance()) {
                mergedText = recordB.text();
            } else {
                mergedText = recordA.timestampMs() >= recordB.timestampMs() ? recordA.text() : recordB.text();
            }
        }

        // 2. Vector computation
        float[] mergedVector = null;
        if (usedLlm && embeddingProvider != null) {
            try {
                mergedVector = embeddingProvider.embed(mergedText).vector();
            } catch (Exception e) {
                log.warn("MemoryMerger: Failed to embed merged text, falling back to source vector: {}", e.getMessage());
            }
        }

        if (mergedVector == null) {
            // Reconstruct/use the vector of the selected record
            byte[] quantizedSource = mergedText.equals(recordB.text()) ? recordB.quantizedVector() : recordA.quantizedVector();
            if (quantizedSource == null) {
                quantizedSource = recordA.quantizedVector() != null ? recordA.quantizedVector() : recordB.quantizedVector();
            }
            if (quantizedSource != null) {
                mergedVector = quantizer.decode(quantizedSource);
            }
        }

        // 3. Metadata merge
        float importance = Math.max(recordA.importance(), recordB.importance());
        long timestampMs = Math.max(recordA.timestampMs(), recordB.timestampMs());
        long synapticTags = recordA.synapticTags() | recordB.synapticTags();
        byte valence = (byte) ((recordA.valence() + recordB.valence()) / 2);
        byte arousal = (byte) ((Byte.toUnsignedInt(recordA.arousal()) + Byte.toUnsignedInt(recordB.arousal())) / 2);
        float storageStrength = Math.max(recordA.storageStrength(), recordB.storageStrength());

        return new MergedMemory(mergedText, mergedVector, importance, timestampMs, synapticTags, valence, arousal, storageStrength);
    }
}
