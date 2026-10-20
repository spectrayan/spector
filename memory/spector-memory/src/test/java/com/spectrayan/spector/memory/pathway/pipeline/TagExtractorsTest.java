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

import com.spectrayan.spector.config.model.TagExtractorMode;
import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TagExtractors}: the configured mode selects the extractor (#1137).
 */
class TagExtractorsTest {

    @Test
    void contentModeSelectsContentExtractor() {
        assertThat(TagExtractors.forMode(TagExtractorMode.CONTENT, new FixedLlm("x")))
                .isInstanceOf(ContentTagExtractor.class);
    }

    @Test
    void nullModeDefaultsToContent() {
        assertThat(TagExtractors.forMode(null, null)).isInstanceOf(ContentTagExtractor.class);
    }

    @Test
    void llmModeSelectsLlmExtractorWhenProviderPresent() {
        assertThat(TagExtractors.forMode(TagExtractorMode.LLM, new FixedLlm("x")))
                .isInstanceOf(LlmTagExtractor.class);
    }

    @Test
    void llmModeWithoutProviderFallsBackToContent() {
        assertThat(TagExtractors.forMode(TagExtractorMode.LLM, null)).isInstanceOf(ContentTagExtractor.class);
    }

    @Test
    void noneModeExtractsNothing() {
        TagExtractor extractor = TagExtractors.forMode(TagExtractorMode.NONE, new FixedLlm("x"));
        assertThat(extractor).isSameAs(TagExtractor.NONE);
        assertThat(extractor.extract("id", "Sarah loves the cherry bookcase")).isEmpty();
    }

    @Test
    void queriesNeverUseTheLlm() {
        TagExtractor llm = TagExtractors.forMode(TagExtractorMode.LLM, new FixedLlm("x"));
        assertThat(TagExtractors.forQueries(llm)).isInstanceOf(ContentTagExtractor.class);

        TagExtractor content = new ContentTagExtractor();
        assertThat(TagExtractors.forQueries(content)).isSameAs(content);
        assertThat(TagExtractors.forQueries(null)).isNull();
    }

    /** Minimal provider returning a fixed reply. */
    static final class FixedLlm implements LlmProvider {
        private final String reply;

        FixedLlm(String reply) {
            this.reply = reply;
        }

        @Override
        public LlmResponse generate(LlmRequest request, GenerationOptions options) {
            return new LlmResponse(reply, 1, 1, "fixed");
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String modelName() {
            return "fixed";
        }
    }
}
