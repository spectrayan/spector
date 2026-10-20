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
package com.spectrayan.spector.memory.pathway.pipeline;

import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the affect appraisal and the fallbacks of {@link LlmTagExtractor} (#1137).
 */
class LlmTagExtractorAppraisalTest {

    private static final String TEXT = "Sarah is really happy with how the cherry bookcase looks in the evening light.";

    @Test
    void parsesTagsValenceAndArousal() {
        var extractor = new LlmTagExtractor(new TagExtractorsTest.FixedLlm(
                "TAGS: woodworking, cherry-bookcase, family\nVALENCE: 60\nAROUSAL: 90"));

        TagExtractionResult result = extractor.extractWithContext("mem-1", TEXT);

        assertThat(result.tags()).containsExactly("woodworking", "cherry-bookcase", "family");
        assertThat(result.valence()).isEqualTo((byte) 60);
        assertThat(Byte.toUnsignedInt(result.arousal())).isEqualTo(90);
        assertThat(result.hasEmotionalContext()).isTrue();
    }

    @Test
    void fallsBackToContentTagsWhenTheLlmThrows() {
        var extractor = new LlmTagExtractor(new ThrowingLlm());

        TagExtractionResult result = extractor.extractWithContext("mem-1", TEXT);

        assertThat(result.tags()).isEqualTo(new ContentTagExtractor().extract("mem-1", TEXT));
        assertThat(result.hasEmotionalContext()).isFalse();
    }

    @Test
    void fallsBackWhenTheLlmIsUnavailable() {
        var extractor = new LlmTagExtractor(new UnavailableLlm());

        TagExtractionResult result = extractor.extractWithContext("mem-1", TEXT);

        assertThat(result.tags()).isEqualTo(new ContentTagExtractor().extract("mem-1", TEXT));
        assertThat(result.hasEmotionalContext()).isFalse();
    }

    @Test
    void fallsBackWhenTheLlmReturnsBlank() {
        var extractor = new LlmTagExtractor(new TagExtractorsTest.FixedLlm("   "));

        TagExtractionResult result = extractor.extractWithContext("mem-1", TEXT);

        assertThat(result.tags()).isEqualTo(new ContentTagExtractor().extract("mem-1", TEXT));
        assertThat(result.hasEmotionalContext()).isFalse();
    }

    @Test
    void exposesItsFallback() {
        var fallback = new ContentTagExtractor();
        assertThat(new LlmTagExtractor(new ThrowingLlm(), fallback).fallback()).isSameAs(fallback);
    }

    /** Provider that fails every call. */
    static final class ThrowingLlm implements LlmProvider {
        @Override
        public LlmResponse generate(LlmRequest request, GenerationOptions options) {
            throw new IllegalStateException("provider down");
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String modelName() {
            return "throwing";
        }
    }

    /** Provider that reports itself unavailable. */
    static final class UnavailableLlm implements LlmProvider {
        @Override
        public LlmResponse generate(LlmRequest request, GenerationOptions options) {
            throw new AssertionError("must not be called when unavailable");
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public String modelName() {
            return "unavailable";
        }
    }
}
