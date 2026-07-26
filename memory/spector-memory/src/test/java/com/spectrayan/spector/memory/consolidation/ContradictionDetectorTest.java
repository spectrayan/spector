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

import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContradictionDetectorTest {

    @Test
    @DisplayName("null textGenerator always returns false")
    void nullProviderAlwaysReturnsFalse() {
        ContradictionDetector detector = new ContradictionDetector(null);
        assertThat(detector.areContradictory("A", "B")).isFalse();
    }

    @Test
    @DisplayName("Returns true when LLM detects contradiction (YES)")
    void contradictionDetected() {
        ContradictionDetector detector = new ContradictionDetector(createMockProvider("YES"));
        assertThat(detector.areContradictory("The sky is blue", "The sky is red")).isTrue();
    }

    @Test
    @DisplayName("Returns false when LLM detects no contradiction (NO)")
    void noContradictionDetected() {
        ContradictionDetector detector = new ContradictionDetector(createMockProvider("NO"));
        assertThat(detector.areContradictory("The sky is blue", "It is a sunny day")).isFalse();
    }

    @Test
    @DisplayName("Returns true when LLM response contains YES")
    void llmResponseContainingYesIsContradiction() {
        ContradictionDetector detector = new ContradictionDetector(createMockProvider("YES, they contradict each other"));
        assertThat(detector.areContradictory("A", "Not A")).isTrue();
    }

    @Test
    @DisplayName("Returns false when LLM throws an exception")
    void llmExceptionReturnsFalse() {
        LlmProvider throwingProvider = new LlmProvider() {
            @Override
            public LlmResponse generate(LlmRequest request, GenerationOptions options) {
                throw new RuntimeException("LLM failure");
            }
            @Override
            public String modelName() { return "test-model"; }
            @Override
            public boolean isAvailable() { return true; }
        };
        ContradictionDetector detector = new ContradictionDetector(throwingProvider);
        assertThat(detector.areContradictory("A", "B")).isFalse();
    }

    @Test
    @DisplayName("Returns false when LLM response is null")
    void nullResponseReturnsFalse() {
        ContradictionDetector detector = new ContradictionDetector(createMockProvider(null));
        assertThat(detector.areContradictory("A", "B")).isFalse();
    }

    @Test
    @DisplayName("Handles blank inputs gracefully")
    void blankInputsHandledGracefully() {
        ContradictionDetector detector = new ContradictionDetector(createMockProvider("NO"));
        assertThat(detector.areContradictory("", "")).isFalse();
    }

    private LlmProvider createMockProvider(String expectedResponse) {
        return new LlmProvider() {
            @Override
            public LlmResponse generate(LlmRequest request, GenerationOptions options) {
                return new LlmResponse(expectedResponse, 10, 10, "test-model");
            }
            @Override
            public String modelName() {
                return "test-model";
            }
            @Override
            public boolean isAvailable() {
                return true;
            }
        };
    }
}
