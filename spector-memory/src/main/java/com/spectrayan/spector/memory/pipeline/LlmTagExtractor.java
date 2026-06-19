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
package com.spectrayan.spector.memory.pipeline;

import com.spectrayan.spector.commons.ResourceUtils;
import com.spectrayan.spector.embed.TextGenerationProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Locale;

/**
 * LLM-powered tag extractor that uses a {@link TextGenerationProvider}
 * to extract semantic tags from document content.
 *
 * <h3>How It Works</h3>
 * <p>
 * Sends a structured prompt to the LLM asking it to identify 5–10
 * contextual tags from the text. The LLM returns comma-separated tags
 * which are parsed into the synaptic tag array.
 * </p>
 *
 * <h3>Fallback</h3>
 * <p>
 * If the LLM is unavailable or returns an unparseable response,
 * falls back to {@link ContentTagExtractor} for basic keyword extraction.
 * </p>
 *
 * <h3>Performance Note</h3>
 * <p>
 * LLM inference adds ~500ms–2s per chunk. Use this extractor for
 * high-value ingestion (e.g., user-provided documents) where tag quality
 * justifies the latency. For bulk ingestion of thousands of files,
 * {@link ContentTagExtractor} is recommended.
 * </p>
 *
 * @see TagExtractor
 * @see TextGenerationProvider
 */
public final class LlmTagExtractor implements TagExtractor {

    private static final Logger log = LoggerFactory.getLogger(LlmTagExtractor.class);

    private static final int MAX_TAGS = 30;

    /** Classpath path to the tag extraction prompt template. */
    private static final String PROMPT_RESOURCE = "prompts/tag-extraction.txt";

    private final TextGenerationProvider generator;
    private final TagExtractor fallback;

    /**
     * Creates an LLM tag extractor with the default content-based fallback.
     *
     * @param generator the text generation provider (e.g., Ollama)
     */
    public LlmTagExtractor(TextGenerationProvider generator) {
        this(generator, new ContentTagExtractor());
    }

    /**
     * Creates an LLM tag extractor with a custom fallback.
     *
     * @param generator the text generation provider
     * @param fallback  fallback extractor for when LLM is unavailable
     */
    public LlmTagExtractor(TextGenerationProvider generator, TagExtractor fallback) {
        this.generator = generator;
        this.fallback = fallback;
    }

    @Override
    public String[] extract(String id, String text) {
        return extractWithContext(id, text).tags();
    }

    @Override
    public TagExtractionResult extractWithContext(String id, String text) {
        if (generator == null || !generator.isAvailable()) {
            log.info("[TagExtract] LLM unavailable for '{}', using fallback", truncId(id));
            return TagExtractionResult.tagsOnly(fallback.extract(id, text));
        }

        long startNs = System.nanoTime();
        try {
            String promptTemplate = ResourceUtils.loadResource(PROMPT_RESOURCE);
            String prompt = String.format(promptTemplate, text != null ? text : id);
            log.debug("[TagExtract] LLM prompt for '{}': {} chars", truncId(id), prompt.length());

            String response = generator.generate(prompt);
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

            if (response == null || response.isBlank()) {
                log.info("[TagExtract] LLM returned empty for '{}' ({}ms), using fallback",
                        truncId(id), elapsedMs);
                return TagExtractionResult.tagsOnly(fallback.extract(id, text));
            }

            log.debug("[TagExtract] LLM raw response for '{}': '{}'", truncId(id),
                    response.length() > 200 ? response.substring(0, 200) + "..." : response);

            // Parse structured response: TAGS: ..., VALENCE: ..., AROUSAL: ...
            String tagLine = null;
            byte valence = 0;
            byte arousal = 0;

            for (String line : response.split("\\n")) {
                String trimmed = line.trim();
                if (trimmed.toUpperCase(Locale.ROOT).startsWith("TAGS:")) {
                    tagLine = trimmed.substring(5).trim();
                } else if (trimmed.toUpperCase(Locale.ROOT).startsWith("VALENCE:")) {
                    valence = parseSignedByte(trimmed.substring(8).trim());
                } else if (trimmed.toUpperCase(Locale.ROOT).startsWith("AROUSAL:")) {
                    arousal = parseUnsignedByte(trimmed.substring(8).trim());
                }
            }

            // Fall back to treating entire response as comma-separated tags
            // (backward compat with older prompt format / models that don't follow structure)
            if (tagLine == null) {
                tagLine = response;
            }

            // Parse comma-separated tags
            String[] tags = Arrays.stream(tagLine.split("[,;]"))
                    .map(String::trim)
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .map(s -> s.replaceAll("[^a-z0-9\\- ]", ""))
                    .map(s -> s.replaceAll("\\s+", "-"))
                    .map(s -> s.replaceAll("-{2,}", "-"))
                    .map(s -> s.replaceAll("^-|-$", ""))
                    .filter(s -> !s.isBlank() && s.length() > 1)
                    .distinct()
                    .limit(MAX_TAGS)
                    .toArray(String[]::new);

            if (tags.length == 0) {
                log.info("[TagExtract] LLM tags parsed to empty for '{}' (raw='{}', {}ms), using fallback",
                        truncId(id), response.trim(), elapsedMs);
                return TagExtractionResult.tagsOnly(fallback.extract(id, text));
            }

            log.info("[TagExtract] LLM extracted {} tags for '{}' in {}ms: [{}] (valence={}, arousal={})",
                    tags.length, truncId(id), elapsedMs, String.join(", ", tags), valence, Byte.toUnsignedInt(arousal));
            return new TagExtractionResult(tags, valence, arousal);

        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;
            log.warn("[TagExtract] LLM failed for '{}' in {}ms: {}, using fallback",
                    truncId(id), elapsedMs, e.getMessage());
            return TagExtractionResult.tagsOnly(fallback.extract(id, text));
        }
    }

    /** Parse a signed byte value from LLM output, clamping to [-128, 127]. */
    private static byte parseSignedByte(String s) {
        try {
            int val = Integer.parseInt(s.replaceAll("[^\\-0-9]", ""));
            return (byte) Math.clamp(val, -128, 127);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Parse an unsigned byte value from LLM output, clamping to [0, 255]. */
    private static byte parseUnsignedByte(String s) {
        try {
            int val = Integer.parseInt(s.replaceAll("[^0-9]", ""));
            return (byte) Math.clamp(val, 0, 255);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Truncate long IDs (file paths) for readable logs. */
    private static String truncId(String id) {
        if (id == null)
            return "null";
        return id.length() > 60 ? "..." + id.substring(id.length() - 57) : id;
    }
}
