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

import com.spectrayan.spector.memory.cortex.consolidation.ContradictionDetector.Verdict;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContradictionDetectorTest {

    @Test
    @DisplayName("null textGenerator yields UNKNOWN (fail closed), never a contradiction")
    void nullProviderIsUnknown() {
        ContradictionDetector detector = new ContradictionDetector(null);
        assertThat(detector.canVerify()).isFalse();
        assertThat(detector.verdict("A", "B")).isEqualTo(Verdict.UNKNOWN);
        assertThat(detector.areContradictory("A", "B")).isFalse();
    }

    @Test
    @DisplayName("Returns CONTRADICTORY when LLM detects contradiction (YES)")
    void contradictionDetected() {
        ContradictionDetector detector = new ContradictionDetector(createMockProvider("YES"));
        assertThat(detector.verdict("The sky is blue", "The sky is red")).isEqualTo(Verdict.CONTRADICTORY);
        assertThat(detector.areContradictory("The sky is blue", "The sky is red")).isTrue();
    }

    @Test
    @DisplayName("Returns COMPATIBLE when LLM detects no contradiction (NO)")
    void noContradictionDetected() {
        ContradictionDetector detector = new ContradictionDetector(createMockProvider("NO"));
        assertThat(detector.verdict("The sky is blue", "It is a sunny day")).isEqualTo(Verdict.COMPATIBLE);
        assertThat(detector.areContradictory("The sky is blue", "It is a sunny day")).isFalse();
    }

    @Test
    @DisplayName("Returns CONTRADICTORY when LLM response starts with YES")
    void llmResponseStartingWithYesIsContradiction() {
        ContradictionDetector detector = new ContradictionDetector(createMockProvider("YES, they contradict each other"));
        assertThat(detector.verdict("A", "Not A")).isEqualTo(Verdict.CONTRADICTORY);
    }

    @Test
    @DisplayName("Returns UNKNOWN when LLM throws an exception")
    void llmExceptionIsUnknown() {
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
        assertThat(detector.verdict("A", "B")).isEqualTo(Verdict.UNKNOWN);
        assertThat(detector.areContradictory("A", "B")).isFalse();
    }

    @Test
    @DisplayName("Returns UNKNOWN when LLM response is null")
    void nullResponseIsUnknown() {
        ContradictionDetector detector = new ContradictionDetector(createMockProvider(null));
        assertThat(detector.verdict("A", "B")).isEqualTo(Verdict.UNKNOWN);
    }

    @Test
    @DisplayName("Returns UNKNOWN for blank or unparseable replies")
    void unparseableResponseIsUnknown() {
        assertThat(new ContradictionDetector(createMockProvider("")).verdict("A", "B")).isEqualTo(Verdict.UNKNOWN);
        assertThat(new ContradictionDetector(createMockProvider("Maybe")).verdict("A", "B")).isEqualTo(Verdict.UNKNOWN);
        assertThat(new ContradictionDetector(createMockProvider("NOT SURE")).verdict("A", "B")).isEqualTo(Verdict.UNKNOWN);
    }

    @Test
    @DisplayName("Parses replies case-insensitively with trailing punctuation")
    void parsesCaseInsensitively() {
        assertThat(ContradictionDetector.parse(" no.")).isEqualTo(Verdict.COMPATIBLE);
        assertThat(ContradictionDetector.parse("Yes!")).isEqualTo(Verdict.CONTRADICTORY);
    }

    @Test
    @DisplayName("Handles blank inputs gracefully")
    void blankInputsHandledGracefully() {
        ContradictionDetector detector = new ContradictionDetector(createMockProvider("NO"));
        assertThat(detector.verdict("", "")).isEqualTo(Verdict.COMPATIBLE);
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
