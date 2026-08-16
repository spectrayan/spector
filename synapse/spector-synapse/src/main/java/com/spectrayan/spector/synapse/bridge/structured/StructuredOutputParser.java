/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-synapse/LICENSE
 *
 * Change Date: July 6, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.synapse.bridge.structured;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser and sanitizer for structured LLM JSON output.
 *
 * <p>Extracts JSON payloads from raw LLM responses, stripping markdown code blocks,
 * preambles, and conversational artifacts, and validates against Jackson target types.</p>
 */
public final class StructuredOutputParser {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private static final Pattern MARKDOWN_JSON_BLOCK = Pattern.compile(
            "```(?:json)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);

    private StructuredOutputParser() {
        // utility class
    }

    /**
     * Extracts and normalizes the JSON substring from raw model output.
     *
     * @param rawOutput the raw string returned by the LLM
     * @return clean JSON string
     * @throws StructuredOutputException if no valid JSON structure could be extracted
     */
    public static String extractJsonString(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            throw new StructuredOutputException("LLM returned empty or null response");
        }

        String trimmed = rawOutput.trim();

        // 1. Try extracting from markdown code block ```json ... ```
        Matcher matcher = MARKDOWN_JSON_BLOCK.matcher(trimmed);
        if (matcher.find()) {
            String blockContent = matcher.group(1).trim();
            if (!blockContent.isEmpty()) {
                return blockContent;
            }
        }

        // 2. Find bounding braces { ... } or brackets [ ... ]
        int firstBrace = trimmed.indexOf('{');
        int firstBracket = trimmed.indexOf('[');

        int startIdx = -1;
        int endIdx = -1;

        if (firstBrace >= 0 && (firstBracket < 0 || firstBrace < firstBracket)) {
            startIdx = firstBrace;
            endIdx = trimmed.lastIndexOf('}');
        } else if (firstBracket >= 0) {
            startIdx = firstBracket;
            endIdx = trimmed.lastIndexOf(']');
        }

        if (startIdx >= 0 && endIdx > startIdx) {
            return trimmed.substring(startIdx, endIdx + 1).trim();
        }

        return trimmed;
    }

    /**
     * Parses raw output into a Jackson {@link JsonNode}.
     *
     * @param rawOutput raw LLM response text
     * @return parsed JsonNode
     * @throws StructuredOutputException on parsing error
     */
    public static JsonNode parseJsonNode(String rawOutput) {
        String jsonStr = extractJsonString(rawOutput);
        try {
            return MAPPER.readTree(jsonStr);
        } catch (JsonProcessingException e) {
            throw new StructuredOutputException("Failed to parse LLM response into JSON: " + e.getOriginalMessage(),
                    rawOutput, null, e);
        }
    }

    /**
     * Deserializes raw output into a strongly-typed Java class or record.
     *
     * @param rawOutput   raw LLM response text
     * @param targetClass target record or class
     * @param <T>         target type
     * @return deserialized instance
     * @throws StructuredOutputException on deserialization or mapping failure
     */
    public static <T> T parseObject(String rawOutput, Class<T> targetClass) {
        Objects.requireNonNull(targetClass, "targetClass must not be null");
        String jsonStr = extractJsonString(rawOutput);
        try {
            return MAPPER.readValue(jsonStr, targetClass);
        } catch (JsonProcessingException e) {
            throw new StructuredOutputException(
                    "Failed to deserialize LLM JSON into " + targetClass.getSimpleName() + ": " + e.getOriginalMessage(),
                    rawOutput, null, e);
        }
    }

    public static ObjectMapper getObjectMapper() {
        return MAPPER;
    }
}
