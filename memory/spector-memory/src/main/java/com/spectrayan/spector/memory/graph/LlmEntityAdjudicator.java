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
import com.spectrayan.spector.provider.generation.GenerationOptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LlmEntityAdjudicator {

    private static final Logger log = LoggerFactory.getLogger(LlmEntityAdjudicator.class);

    private static final String PROMPT_RESOURCE = "prompts/entity-adjudication.txt";

    private static final Pattern THINK_BLOCK = Pattern.compile(
            "<think>.*?</think>", Pattern.DOTALL);

    private static final Pattern DECISION_PATTERN = Pattern.compile(
            "^DECISION:\\s*(MERGE|KEEP_SEPARATE)", Pattern.MULTILINE);
    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile(
            "^CONFIDENCE:\\s*([0-9.]+)", Pattern.MULTILINE);
    private static final Pattern REASON_PATTERN = Pattern.compile(
            "^REASON:\\s*(.+)", Pattern.MULTILINE);

    private static final GenerationOptions DEFAULT_OPTIONS =
            GenerationOptions.builder()
                    .temperature(0.1f)
                    .maxTokens(256)
                    .build();

    private final LlmProvider generator;
    private final GenerationOptions generationOptions;

    public record AdjudicationResult(boolean shouldMerge, float confidence, String reasoning) {}

    public LlmEntityAdjudicator(LlmProvider provider) {
        this(provider, null);
    }

    public LlmEntityAdjudicator(LlmProvider provider, GenerationOptions options) {
        this.generator = provider;
        this.generationOptions = options != null ? options : DEFAULT_OPTIONS;
    }

    public boolean isAvailable() {
        return generator != null && generator.isAvailable();
    }

    public AdjudicationResult adjudicate(String nameA, String typeA,
                                         String nameB, String typeB,
                                         List<String> contextSnippets) {
        if (!isAvailable()) {
            return new AdjudicationResult(false, 0.0f, "LLM not available");
        }
        
        try {
            String promptTemplate = ResourceUtils.loadResource(PROMPT_RESOURCE);
            
            String contextStr = String.join("\n- ", contextSnippets);
            if (!contextStr.isEmpty()) {
                contextStr = "- " + contextStr;
            } else {
                contextStr = "No context available.";
            }

            String prompt = promptTemplate
                    .replace("{nameA}", nameA)
                    .replace("{typeA}", typeA)
                    .replace("{nameB}", nameB)
                    .replace("{typeB}", typeB)
                    .replace("{contextSnippets}", contextStr);

            String response = generator.generate(prompt, generationOptions);
            if (response == null || response.isBlank()) {
                return new AdjudicationResult(false, 0.0f, "Empty response");
            }

            return parseResponse(response);
        } catch (Exception e) {
            log.warn("Entity adjudication failed", e);
            return new AdjudicationResult(false, 0.0f, "Error: " + e.getMessage());
        }
    }

    private AdjudicationResult parseResponse(String rawResponse) {
        String response = THINK_BLOCK.matcher(rawResponse).replaceAll("").strip();
        response = response.replaceAll("```[a-z]*\n?", "").strip();

        Matcher decisionMatcher = DECISION_PATTERN.matcher(response);
        boolean merge = false;
        if (decisionMatcher.find()) {
            merge = "MERGE".equals(decisionMatcher.group(1));
        } else {
            return new AdjudicationResult(false, 0.0f, "Could not parse DECISION");
        }

        Matcher confidenceMatcher = CONFIDENCE_PATTERN.matcher(response);
        float confidence = 0.0f;
        if (confidenceMatcher.find()) {
            try {
                confidence = Float.parseFloat(confidenceMatcher.group(1));
            } catch (NumberFormatException ignored) {}
        }

        Matcher reasonMatcher = REASON_PATTERN.matcher(response);
        String reason = "";
        if (reasonMatcher.find()) {
            reason = reasonMatcher.group(1).trim();
        }

        return new AdjudicationResult(merge, confidence, reason);
    }
}
