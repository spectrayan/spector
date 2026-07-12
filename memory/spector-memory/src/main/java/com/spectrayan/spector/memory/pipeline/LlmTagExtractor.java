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
import java.util.Set;

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

    private static final int MAX_TAGS = 15;
    /** Max length for a single tag — anything longer is almost certainly prompt leakage. */
    private static final int MAX_TAG_LENGTH = 50;

    /**
     * Blocklist of pronouns and generic words that should never become tags.
     * Applied as a post-extraction safety net since LLMs sometimes ignore
     * prompt instructions about this.
     */
    private static final Set<String> BLOCKED_TAGS = Set.of(
            "i", "me", "my", "he", "him", "his", "she", "her", "hers",
            "it", "its", "we", "us", "our", "they", "them", "their",
            "you", "your", "this", "that", "these", "those",
            "someone", "somebody", "something", "anyone", "anybody", "anything",
            "everyone", "everybody", "everything", "nobody", "nothing",
            "stuff", "things", "thing", "people", "person",
            "who", "whom", "whose", "which", "what",
            "the", "a", "an", "tag"
    );

    /**
     * Substrings that indicate prompt leakage — if a tag contains any of these,
     * the LLM is echoing its instructions instead of extracting from the text.
     */
    private static final Set<String> PROMPT_LEAK_FRAGMENTS = Set.of(
            "hyphenated", "multi-word", "no-markdown", "derived-from",
            "no-spaces", "no-symbols", "output-format", "lowercase",
            "no-punctuation", "extract-tags", "directly-derived",
            "except-hyphens", "multi-word-tags", "formatting-rules",
            "tag-formatting", "emotional-tone", "no-explanations",
            "tag1", "tag2", "tag3", "valence-number", "arousal-number",
            "valence-score", "arousal-score", "sentiment-score", "intensity-score",
            "extracted-tags", "comma-separated",
            "valence", "arousal", "tagging-complete", "tag-extraction"
    );

    /**
     * Patterns to extract valence/arousal values embedded in the tag stream.
     * Models sometimes output: "cleanliness/valence-105/arousal-234" or
     * "hairvalence-56arousal-192" — these patterns catch all variants:
     * "valence-56", "valence:-56", "valence56", embedded mid-word.
     */
    private static final java.util.regex.Pattern EMBEDDED_VALENCE = java.util.regex.Pattern.compile(
            "(?i)valence[:\\s-]*([-]?\\d{1,3})");
    private static final java.util.regex.Pattern EMBEDDED_AROUSAL = java.util.regex.Pattern.compile(
            "(?i)arousal[:\\s-]*([-]?\\d{1,3})");

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

            // Prepare text: strip markdown formatting for small models
            // (text is already chunked to configured size by TextChunker)
            String cleanText = text != null ? stripMarkdown(text) : id;
            if (cleanText.isBlank()) {
                log.info("[TagExtract] Text empty after markdown stripping for '{}', using fallback", truncId(id));
                return TagExtractionResult.tagsOnly(fallback.extract(id, text));
            }

            String prompt = String.format(promptTemplate, cleanText);
            log.debug("[TagExtract] LLM prompt for '{}': {} chars (text: {} → {})",
                    truncId(id), prompt.length(), text != null ? text.length() : 0, cleanText.length());

            String response = generator.generate(prompt);
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

            if (response == null || response.isBlank()) {
                log.info("[TagExtract] LLM returned empty for '{}' ({}ms), using fallback",
                        truncId(id), elapsedMs);
                return TagExtractionResult.tagsOnly(fallback.extract(id, text));
            }

            log.info("[TagExtract] LLM raw response for '{}': '{}'", truncId(id),
                    response.length() > 300 ? response.substring(0, 300) + "..." : response);

            // Parse structured response: TAGS: ..., VALENCE: ..., AROUSAL: ...
            String tagLine = null;
            byte valence = 0;
            byte arousal = 0;

            for (String line : response.split("\\n")) {
                // Remove all formatting chars that might wrap the label (like asterisks, underscores, backticks)
                String cleaned = line.replace("*", "").replace("_", "-").replace("`", "").trim();
                String upper = cleaned.toUpperCase(Locale.ROOT);

                int tagsIdx = upper.indexOf("TAGS:");
                if (tagsIdx >= 0) {
                    tagLine = cleaned.substring(tagsIdx + 5).trim();
                    continue;
                }

                int valenceIdx = upper.indexOf("VALENCE:");
                if (valenceIdx >= 0) {
                    valence = parseSignedByte(cleaned.substring(valenceIdx + 8).trim());
                    continue;
                }

                int arousalIdx = upper.indexOf("AROUSAL:");
                if (arousalIdx >= 0) {
                    arousal = parseUnsignedByte(cleaned.substring(arousalIdx + 8).trim());
                    continue;
                }
            }

            // Fall back to treating entire response as comma-separated tags
            // (backward compat with older prompt format / models that don't follow structure)
            if (tagLine == null) {
                // Check if the response uses slash-separated format (e.g., /tag1/tag2/tag3)
                boolean hasSlashTags = response.contains("/") && response.chars().filter(c -> c == '/').count() >= 2;

                if (hasSlashTags) {
                    // Model produced slash-separated tags — normalize to comma-separated
                    log.debug("[TagExtract] LLM response for '{}' uses slash-separated format, normalizing", truncId(id));
                    tagLine = response.replace("/", ",");
                } else if (!response.contains(",") && !response.contains(";") && response.length() > 100) {
                    // Safety: if the response has no commas/semicolons/slashes and is very long,
                    // the model likely echoed the prompt — fall back immediately
                    log.warn("[TagExtract] LLM response for '{}' appears to be prompt echo ({}chars, no delimiters), using fallback. Response: {}",
                            truncId(id), response.length(),
                            response.length() > 300 ? response.substring(0, 300) + "..." : response);
                    return TagExtractionResult.tagsOnly(fallback.extract(id, text));
                } else {
                    tagLine = response;
                }
            }

            // ── Smart extraction of valence/arousal embedded in the tag stream ──
            // Models sometimes concatenate everything: "cleanliness/valence-105/arousal-234"
            // which after slash→comma becomes "cleanliness,valence-105,arousal-234"
            // or even merged: "cleanlinessvalence-105arousal-234"
            // We extract valence/arousal values before splitting tags.
            if (valence == 0) {
                java.util.regex.Matcher valMatcher = EMBEDDED_VALENCE.matcher(tagLine);
                if (valMatcher.find()) {
                    valence = parseSignedByte(valMatcher.group(1));
                    tagLine = valMatcher.replaceAll("");
                    log.debug("[TagExtract] Extracted embedded valence={} from tag stream", valence);
                }
            }
            if (arousal == 0) {
                java.util.regex.Matcher arMatcher = EMBEDDED_AROUSAL.matcher(tagLine);
                if (arMatcher.find()) {
                    arousal = parseUnsignedByte(arMatcher.group(1));
                    tagLine = arMatcher.replaceAll("");
                    log.debug("[TagExtract] Extracted embedded arousal={} from tag stream", arousal);
                }
            }

            // Also strip any "tagging-complete" or "taggingcomplete" fragments the model appends
            tagLine = tagLine.replaceAll("(?i)tagging[- ]?complete", "");

            // Parse comma-separated tags (slashes already normalized above)
            String[] tags = Arrays.stream(tagLine.split("[,;]"))
                    .map(String::trim)
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .map(s -> s.replace('_', '-'))
                    .map(s -> s.replaceAll("[^a-z0-9\\- ]", ""))
                    .map(s -> s.replaceAll("\\s+", "-"))
                    .map(s -> s.replaceAll("-{2,}", "-"))
                    .map(s -> s.replaceAll("^-|-$", ""))
                    .filter(s -> !s.isBlank() && s.length() > 1)
                    .filter(s -> s.length() <= MAX_TAG_LENGTH)
                    .filter(s -> !BLOCKED_TAGS.contains(s))
                    .filter(s -> !isPromptLeak(s))
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

    /** Returns true if a tag contains known prompt instruction fragments. */
    private static boolean isPromptLeak(String tag) {
        if (tag == null) return true;
        if (tag.matches("tag\\d+")) return true;
        for (String fragment : PROMPT_LEAK_FRAGMENTS) {
            if (tag.contains(fragment)) return true;
        }
        return false;
    }

    /**
     * Strips markdown formatting to produce clean plaintext for the LLM.
     *
     * <p>Removes headers, bold/italic, links, images, code fences, HTML tags,
     * bullet markers, blockquotes, and horizontal rules. Collapses whitespace.</p>
     */
    public static String stripMarkdown(String md) {
        if (md == null || md.isBlank()) return "";
        String s = md;
        // Remove code fences (```...```)
        s = s.replaceAll("(?s)```[a-z]*\n.*?```", " ");
        // Remove inline code (`...`)
        s = s.replaceAll("`([^`]+)`", "$1");
        // Remove images: ![alt](url)
        s = s.replaceAll("!\\[([^]]*)]\\([^)]+\\)", "$1");
        // Remove links: [text](url) → text
        s = s.replaceAll("\\[([^]]*)]\\([^)]+\\)", "$1");
        // Remove HTML tags
        s = s.replaceAll("<[^>]+>", " ");
        // Remove headers (# ... ######)
        s = s.replaceAll("(?m)^#{1,6}\\s+", "");
        // Remove bold/italic markers
        s = s.replaceAll("\\*{1,3}([^*]+)\\*{1,3}", "$1");
        s = s.replaceAll("_{1,3}([^_]+)_{1,3}", "$1");
        // Remove blockquotes
        s = s.replaceAll("(?m)^>+\\s*", "");
        // Remove horizontal rules
        s = s.replaceAll("(?m)^[-*_]{3,}$", "");
        // Remove bullet/list markers
        s = s.replaceAll("(?m)^\\s*[-*+]\\s+", "");
        s = s.replaceAll("(?m)^\\s*\\d+\\.\\s+", "");
        // Collapse whitespace
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }
}
