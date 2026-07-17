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
package com.spectrayan.spector.memory.graph;

import com.spectrayan.spector.commons.ResourceUtils;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.memory.error.SpectorEntityGraphException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-powered entity extractor using a {@link LlmProvider}.
 *
 * <h3>How It Works</h3>
 * <p>Sends a structured prompt to the LLM asking it to identify entities
 * and their relationships from the text. The prompt is loaded from the
 * classpath resource {@code prompts/entity-extraction.txt} and cached
 * by {@link ResourceUtils}. The LLM returns a simple line-based format
 * which is parsed into {@link ExtractedEntity} records.</p>
 *
 * <h3>Output Format Expected from LLM</h3>
 * <pre>
 *   ENTITY: Alice | PERSON
 *   ENTITY: Project Alpha | PROJECT
 *   RELATION: Alice | MANAGES | Project Alpha
 * </pre>
 *
 * <h3>Fallback</h3>
 * <p>If the LLM is unavailable or returns unparseable output,
 * returns an empty list (graceful degradation).</p>
 *
 * <h3>Performance Note</h3>
 * <p>LLM inference adds ~500ms - 2s per memory. Use this extractor for
 * high-value ingestion where entity quality justifies the latency.</p>
 *
 * @see EntityExtractor
 * @see LlmProvider
 * @see ResourceUtils
 */
public final class LlmEntityExtractor implements EntityExtractor {

    private static final Logger log = LoggerFactory.getLogger(LlmEntityExtractor.class);

    /** Classpath path to the entity extraction prompt template. */
    private static final String PROMPT_RESOURCE = "prompts/entity-extraction.txt";

    /** Pattern to strip <think>...</think> reasoning blocks from qwen3 output. */
    private static final Pattern THINK_BLOCK = Pattern.compile(
            "<think>.*?</think>", Pattern.DOTALL);
    private static final int DEFAULT_MAX_ENTITIES = 10;
    private static final int DEFAULT_MAX_RELATIONS = 20;

    private static final Pattern ENTITY_PATTERN = Pattern.compile(
            "^ENTITY:\\s*(.+?)\\s*\\|\\s*(\\w+)\\s*$", Pattern.MULTILINE);
    private static final Pattern RELATION_PATTERN = Pattern.compile(
            "^RELATION:\\s*(.+?)\\s*\\|\\s*([\\w\\- ]+?)\\s*\\|\\s*(.+?)\\s*$", Pattern.MULTILINE);

    /**
     * Fallback: 2-field relation pattern for models that merge the source entity
     * into the relation type name (e.g., RELATION: JOHN_SMITH_REPORTS_TO | SARAH_CHEN).
     */
    private static final Pattern RELATION_2FIELD_PATTERN = Pattern.compile(
            "^RELATION:\\s*([\\w\\-]+?)\\s*\\|\\s*(.+?)\\s*$", Pattern.MULTILINE);

    /**
     * Hard blocklist for pronouns and generic placeholders that LLMs sometimes
     * produce despite prompt instructions. Checked case-insensitively against
     * entity names and relation source/target names.
     */
    private static final Set<String> BLOCKED_ENTITY_NAMES = Set.of(
            // Personal pronouns
            "i", "me", "my", "mine", "myself",
            "he", "him", "his", "himself",
            "she", "her", "hers", "herself",
            "it", "its", "itself",
            "we", "us", "our", "ours", "ourselves",
            "they", "them", "their", "theirs", "themselves",
            "you", "your", "yours", "yourself", "yourselves",
            // Demonstratives and generics
            "this", "that", "these", "those",
            "someone", "somebody", "something", "somewhere",
            "anyone", "anybody", "anything", "anywhere",
            "everyone", "everybody", "everything", "everywhere",
            "no one", "nobody", "nothing", "nowhere",
            "stuff", "things", "thing", "people", "person",
            // Relative and interrogative
            "who", "whom", "whose", "which", "what",
            // Common non-entity words the model sometimes produces
            "the", "a", "an", "user", "speaker", "narrator",
            // Template instructions / placeholders
            "name", "source", "target", "type", "relation", "relation_type"
    );

    private final LlmProvider generator;
    private final int maxEntities;
    private final int maxRelations;
    private final com.spectrayan.spector.provider.generation.GenerationOptions generationOptions;

    /** Default generation options for entity extraction. */
    private static final com.spectrayan.spector.provider.generation.GenerationOptions DEFAULT_OPTIONS =
            com.spectrayan.spector.provider.generation.GenerationOptions.builder()
                    .temperature(0.3f)
                    .maxTokens(1024)
                    .topP(0.95f)
                    .build();

    /**
     * Creates an LLM entity extractor with default limits.
     *
     * @param generator the text generation provider
     */
    public LlmEntityExtractor(LlmProvider generator) {
        this(generator, DEFAULT_MAX_ENTITIES, DEFAULT_MAX_RELATIONS, null);
    }

    /**
     * Creates an LLM entity extractor with custom limits.
     *
     * @param generator    the text generation provider
     * @param maxEntities  maximum entities to extract per memory
     * @param maxRelations maximum relations to extract per memory
     */
    public LlmEntityExtractor(LlmProvider generator,
                               int maxEntities, int maxRelations) {
        this(generator, maxEntities, maxRelations, null);
    }

    /**
     * Creates an LLM entity extractor with custom limits and generation options.
     *
     * @param generator    the text generation provider
     * @param maxEntities  maximum entities to extract per memory
     * @param maxRelations maximum relations to extract per memory
     * @param options      generation options (temperature, maxTokens, topP); null uses defaults
     */
    public LlmEntityExtractor(LlmProvider generator,
                               int maxEntities, int maxRelations,
                               com.spectrayan.spector.provider.generation.GenerationOptions options) {
        this.generator = generator;
        this.maxEntities = maxEntities;
        this.maxRelations = maxRelations;
        this.generationOptions = options != null ? options : DEFAULT_OPTIONS;
    }

    @Override
    public List<ExtractedEntity> extract(String id, String text) {
        if (generator == null || !generator.isAvailable()) {
            return List.of();
        }

        try {
            // Strip markdown to make text clean for small models
            String cleanText = text != null ? com.spectrayan.spector.memory.pipeline.LlmTagExtractor.stripMarkdown(text) : id;
            if (cleanText.isBlank()) {
                log.info("[EntityExtract] Text empty after markdown stripping for '{}', skipping", id);
                return List.of();
            }

            // Load prompt template from classpath (cached by ResourceUtils)
            String promptTemplate = ResourceUtils.loadResource(PROMPT_RESOURCE);
            String prompt = String.format(promptTemplate,
                    maxEntities, maxRelations,
                    cleanText);
            String response = generator.generate(prompt, generationOptions);

            if (response == null || response.isBlank()) {
                log.info("[EntityExtract] LLM returned empty for '{}', skipping", id);
                return List.of();
            }

            // Log raw response for diagnostics (truncated)
            String preview = response.length() > 300 ? response.substring(0, 300) + "..." : response;
            log.info("[EntityExtract] Raw LLM response for '{}': {}", id, preview.replaceAll("\\n", " | "));

            return parseResponse(response, id);

        } catch (RuntimeException e) {
            SpectorEntityGraphException ex = new SpectorEntityGraphException("LLM extraction", e);
            log.warn(ex.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean isAvailable() {
        return generator != null && generator.isAvailable();
    }

    /**
     * Parses the LLM response into extracted entities with relations.
     */
    private List<ExtractedEntity> parseResponse(String rawResponse, String id) {
        // Strip <think>...</think> reasoning blocks (qwen3 models)
        String response = THINK_BLOCK.matcher(rawResponse).replaceAll("").strip();
        // Strip markdown code fences if model wraps output
        response = response.replaceAll("```[a-z]*\n?", "").strip();

        if (response.isBlank()) {
            log.info("[EntityExtract] Response was all <think> content for '{}', no entities", id);
            return List.of();
        }

        // Parse entities
        List<String> entityNames = new ArrayList<>();
        List<String> entityTypes = new ArrayList<>();

        Matcher entityMatcher = ENTITY_PATTERN.matcher(response);
        int entityCount = 0;
        while (entityMatcher.find() && entityCount < maxEntities) {
            String name = entityMatcher.group(1).trim();
            String typeStr = entityMatcher.group(2).trim().toUpperCase(Locale.ROOT);

            // Filter out pronouns and generic placeholders
            if (isBlockedName(name)) {
                log.debug("[EntityExtract] Filtered blocked entity: '{}'", name);
                continue;
            }

            entityNames.add(name);
            entityTypes.add(typeStr);
            entityCount++;
        }

        if (entityNames.isEmpty()) {
            log.debug("No entities parsed from LLM response for '{}'", id);
            return List.of();
        }

        // Parse relations  --  with format auto-detection.
        // Expected format: RELATION: source | RELATION_TYPE | target
        // But many models produce: RELATION: RELATION_TYPE | source_or_desc | target
        // We detect the swap by checking whether group(1) looks like a SCREAMING_SNAKE_CASE
        // relation type rather than an entity name.
        List<RelationTriple> relations = new ArrayList<>();
        Set<String> entityNameSet = new java.util.HashSet<>();
        for (String n : entityNames) entityNameSet.add(n.toLowerCase(Locale.ROOT));

        Matcher relationMatcher = RELATION_PATTERN.matcher(response);
        int relationCount = 0;
        while (relationMatcher.find() && relationCount < maxRelations) {
            String g1 = relationMatcher.group(1).trim();
            String g2 = relationMatcher.group(2).trim();
            String g3 = relationMatcher.group(3).trim();

            String source, relTypeStr, target;

            // Detect format: if g1 looks like a SCREAMING_SNAKE_CASE type (no lowercase
            // letters, contains underscore or is all uppercase) and g1 does NOT match any
            // known entity, assume the model swapped the format.
            boolean g1IsRelType = isScreamingSnakeCase(g1) && !entityNameSet.contains(g1.toLowerCase(Locale.ROOT));
            boolean g2MatchesEntity = entityNameSet.contains(g2.toLowerCase(Locale.ROOT));
            boolean g3MatchesEntity = entityNameSet.contains(g3.toLowerCase(Locale.ROOT));

            if (g1IsRelType && (g2MatchesEntity || g3MatchesEntity)) {
                // Swapped format: RELATION: REL_TYPE | entity_a | entity_b
                relTypeStr = g1.toUpperCase(Locale.ROOT).replaceAll("[- ]+", "_");
                source = g2;
                target = g3;
                log.debug("[EntityExtract] Fixed swapped relation format: {} | {} | {}  ->  {}  ->  {}  ->  {}",
                        g1, g2, g3, source, relTypeStr, target);
            } else {
                // Standard format: RELATION: source | RELATION_TYPE | target
                source = g1;
                relTypeStr = g2.toUpperCase(Locale.ROOT).replaceAll("[- ]+", "_");
                target = g3;
            }

            // Filter out relations involving pronouns or generic placeholders
            if (isBlockedName(source) || isBlockedName(target)) {
                log.debug("[EntityExtract] Filtered blocked relation: {} | {} | {}", source, relTypeStr, target);
                continue;
            }

            relations.add(new RelationTriple(source, relTypeStr, target));
            relationCount++;
        }

        // Fallback: 2-field relation pattern for models that merge the source entity
        // into the relation type (e.g., RELATION: JOHN_SMITH_REPORTS_TO | SARAH_CHEN).
        // Only attempt if 3-field pattern yielded nothing.
        if (relations.isEmpty()) {
            Matcher twoFieldMatcher = RELATION_2FIELD_PATTERN.matcher(response);
            while (twoFieldMatcher.find() && relationCount < maxRelations) {
                String mergedType = twoFieldMatcher.group(1).trim();
                String target = twoFieldMatcher.group(2).trim();

                // Try to decompose: find which entity name is a prefix of the merged type
                // e.g., JOHN_SMITH_REPORTS_TO  ->  source=John_Smith, type=REPORTS_TO
                String source = null;
                String relTypeStr = mergedType;
                String mergedUpper = mergedType.toUpperCase(Locale.ROOT);

                for (String eName : entityNames) {
                    String eUpper = eName.toUpperCase(Locale.ROOT).replace(" ", "_");
                    if (mergedUpper.startsWith(eUpper + "_")) {
                        source = eName;
                        relTypeStr = mergedUpper.substring(eUpper.length() + 1);
                        break;
                    }
                }

                // Also check if target matches an entity
                if (isBlockedName(target)) continue;
                boolean targetIsEntity = entityNameSet.contains(target.toLowerCase(Locale.ROOT))
                        || entityNameSet.contains(target.replace("_", " ").toLowerCase(Locale.ROOT));

                if (source != null && targetIsEntity) {
                    log.debug("[EntityExtract] Decomposed 2-field relation: {} | {}  ->  {} | {} | {}",
                            mergedType, target, source, relTypeStr, target);
                    relations.add(new RelationTriple(source, relTypeStr, target));
                    relationCount++;
                } else if (targetIsEntity) {
                    // Can't decompose source  --  use RELATED_TO with first entity as source
                    log.debug("[EntityExtract] 2-field relation with unknown source: {} | {}",
                            mergedType, target);
                }
            }
        }

        // Build ExtractedEntity list with attached relations
        List<ExtractedEntity> result = new ArrayList<>();
        for (int i = 0; i < entityNames.size(); i++) {
            String name = entityNames.get(i);
            String type = entityTypes.get(i);

            // Collect relations where this entity is the source OR target
            // (entity participates in the relation from either direction)
            List<EntityRelation> entityRelations = relations.stream()
                    .filter(r -> r.source.equalsIgnoreCase(name))
                    .map(r -> new EntityRelation(r.target, r.type))
                    .toList();

            result.add(new ExtractedEntity(name, type, entityRelations));
        }

        int attachedRelations = result.stream().mapToInt(e -> e.relations().size()).sum();
        log.info("[EntityExtract] LLM extracted {} entities, {} relations ({} attached) for '{}': {}",
                entityNames.size(), relations.size(), attachedRelations, id,
                entityNames.stream().collect(java.util.stream.Collectors.joining(", ")));
        return result;
    }

    private record RelationTriple(String source, String type, String target) {}

    /** Pattern for SCREAMING_SNAKE_CASE identifiers (e.g., DEPENDS_ON, MANAGES). */
    private static final Pattern SCREAMING_SNAKE = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    /**
     * Returns true if the string looks like a SCREAMING_SNAKE_CASE relation type
     * (e.g., DEPENDS_ON, MANAGES, PART_OF) rather than an entity name.
     */
    private static boolean isScreamingSnakeCase(String s) {
        if (s == null || s.length() < 2) return false;
        return SCREAMING_SNAKE.matcher(s).matches();
    }

    /**
     * Returns true if the given name is a pronoun, generic placeholder,
     * template instruction, or contains prompt placeholders that should not be stored.
     */
    private static boolean isBlockedName(String name) {
        if (name == null || name.isBlank()) return true;
        String lower = name.toLowerCase(Locale.ROOT).strip();
        
        // Block prompt instructions / placeholder patterns
        if (lower.contains("<") || lower.contains(">") || lower.contains("[text_start]") || lower.contains("[text_end]")) {
            return true;
        }
        if (lower.contains("source entity") || lower.contains("target entity") || lower.contains("relation type")) {
            return true;
        }
        
        // Check single-word names against blocklist
        if (BLOCKED_ENTITY_NAMES.contains(lower)) return true;
        // Also block single-character names (common LLM artifact)
        return lower.length() <= 1;
    }
}

