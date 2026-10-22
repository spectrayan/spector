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

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.commons.error.SpectorConfigException;
import java.util.List;

import com.spectrayan.spector.config.model.TagExtractorMode;
import com.spectrayan.spector.provider.extraction.SpanExtractor;
import com.spectrayan.spector.provider.generation.LlmProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps the configured {@code spector.memory.tag-extractor} mode to a {@link TagExtractor}.
 *
 * <ul>
 *   <li>{@link TagExtractorMode#CONTENT} (default): {@link ContentTagExtractor}, keyword based.</li>
 *   <li>{@link TagExtractorMode#LLM}: a {@linkplain LlmTagExtractor#strict strict} {@link LlmTagExtractor}
 *       over the memory's generation provider. It also appraises valence and arousal. There is no
 *       keyword fallback: with no provider at all this throws {@link ErrorCode#GENERATION_PROVIDER_UNAVAILABLE}
 *       at build time, and a write whose LLM call fails throws {@link ErrorCode#MEMORY_LLM_APPRAISAL_FAILED}.
 *       Silently storing keyword tags and neutral affect would hide a broken configuration.</li>
 *   <li>{@link TagExtractorMode#NONE}: {@link TagExtractor#NONE}, no tags.</li>
 *   <li>{@link TagExtractorMode#GLINER}: {@link GlinerTagExtractor} over the shared span session.
 *       A missing model fails startup. An inference error fails the write. There is no keyword fallback.</li>
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
     * @throws SpectorConfigException with {@link ErrorCode#GENERATION_PROVIDER_UNAVAILABLE} when the mode is
     *                                {@link TagExtractorMode#LLM} and {@code llmProvider} is {@code null}
     */
    public static TagExtractor forMode(TagExtractorMode mode, LlmProvider llmProvider) {
        return forMode(mode, llmProvider, null, List.of());
    }

    /**
     * Resolves the extractor, including a GLiNER session when the mode is
     * {@link TagExtractorMode#GLINER}.
     *
     * @param mode         configured mode; {@code null} means content
     * @param llmProvider  generation provider; required for {@link TagExtractorMode#LLM}
     * @param spans        open GLiNER session; required for {@link TagExtractorMode#GLINER}
     * @param glinerLabels prompt labels for GLiNER
     * @return the extractor
     */
    public static TagExtractor forMode(TagExtractorMode mode, LlmProvider llmProvider,
                                        SpanExtractor spans, List<String> glinerLabels) {
        TagExtractorMode effective = mode != null ? mode : TagExtractorMode.CONTENT;
        return switch (effective) {
            case NONE -> TagExtractor.NONE;
            case GLINER -> {
                if (spans == null) {
                    throw new SpectorConfigException(ErrorCode.EXTRACTION_MODEL_INVALID, "gliner",
                            "tag-extractor=gliner requires spector.provider.extraction.model-path");
                }
                yield new GlinerTagExtractor(spans, glinerLabels == null ? List.of() : glinerLabels);
            }
            case LLM -> {
                if (llmProvider == null) {
                    log.error("[TagExtract] tag-extractor=llm but no generation provider is configured ({})",
                            ErrorCode.GENERATION_PROVIDER_UNAVAILABLE.id());
                    throw new SpectorConfigException(ErrorCode.GENERATION_PROVIDER_UNAVAILABLE,
                            "spector.memory.tag-extractor=llm",
                            "no generation provider is configured (set spector.provider.generation.*)");
                }
                if (!llmProvider.isAvailable()) {
                    log.error("[TagExtract] tag-extractor=llm but the generation provider '{}' is not available; "
                            + "writes will fail with {} until it is", llmProvider.modelName(),
                            ErrorCode.MEMORY_LLM_APPRAISAL_FAILED.id());
                }
                yield LlmTagExtractor.strict(llmProvider);
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
