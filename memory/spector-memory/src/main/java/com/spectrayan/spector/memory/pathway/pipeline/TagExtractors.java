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
import com.spectrayan.spector.provider.generation.LlmProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps the configured {@code spector.memory.tag-extractor} mode to a {@link TagExtractor}.
 *
 * <ul>
 *   <li>{@link TagExtractorMode#CONTENT} (default): {@link ContentTagExtractor}, keyword based.</li>
 *   <li>{@link TagExtractorMode#LLM}: {@link LlmTagExtractor} over the memory's generation provider. It
 *       also appraises valence and arousal, and falls back to {@link ContentTagExtractor} per call
 *       when the provider is unavailable or fails. With no provider at all, the content extractor is
 *       used and a warning is logged.</li>
 *   <li>{@link TagExtractorMode#NONE}: {@link TagExtractor#NONE}, no tags.</li>
 * </ul>
 *
 * <p>Mirrors how {@code CognitiveGraphBuilder} maps the entity-extraction mode to an extractor.</p>
 */
public final class TagExtractors {

    private static final Logger log = LoggerFactory.getLogger(TagExtractors.class);

    private TagExtractors() {
    }

    /**
     * Resolves the extractor for a configured mode.
     *
     * @param mode        the configured mode; {@code null} means {@link TagExtractorMode#CONTENT}
     * @param llmProvider the memory's generation provider; may be {@code null}
     * @return the extractor to use, never {@code null}
     */
    public static TagExtractor forMode(TagExtractorMode mode, LlmProvider llmProvider) {
        TagExtractorMode effective = mode != null ? mode : TagExtractorMode.CONTENT;
        return switch (effective) {
            case NONE -> TagExtractor.NONE;
            case LLM -> {
                if (llmProvider == null) {
                    log.warn("[TagExtract] tag-extractor=llm but no generation provider is configured; "
                            + "using the content tag extractor");
                    yield new ContentTagExtractor();
                }
                if (!llmProvider.isAvailable()) {
                    log.warn("[TagExtract] tag-extractor=llm but the generation provider is not available yet; "
                            + "calls fall back to the content tag extractor until it is");
                }
                yield new LlmTagExtractor(llmProvider);
            }
            case CONTENT -> new ContentTagExtractor();
        };
    }

    /**
     * Returns an extractor suitable for tagging recall queries: never one that calls an LLM, so a
     * recall does not pay a generation round-trip. For an {@link LlmTagExtractor} this is its fallback.
     *
     * @param extractor the configured extractor; may be {@code null}
     * @return the extractor for queries, or {@code null} when {@code extractor} is {@code null}
     */
    public static TagExtractor forQueries(TagExtractor extractor) {
        if (extractor instanceof LlmTagExtractor llm) {
            return llm.fallback();
        }
        return extractor;
    }
}
