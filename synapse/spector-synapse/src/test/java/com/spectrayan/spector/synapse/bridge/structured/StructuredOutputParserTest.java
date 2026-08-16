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

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredOutputParserTest {

    record SampleResult(String title, int count, boolean active) {}

    @Test
    @DisplayName("Extracts raw JSON string directly")
    void testExtractRawJsonString() {
        String json = "{\"title\":\"Test\",\"count\":5,\"active\":true}";
        String extracted = StructuredOutputParser.extractJsonString(json);
        assertThat(extracted).isEqualTo(json);
    }

    @Test
    @DisplayName("Extracts JSON from markdown code block")
    void testExtractJsonFromMarkdown() {
        String raw = "Here is the response:\n```json\n{\"title\":\"Test\",\"count\":5,\"active\":true}\n```\nHope that helps!";
        String extracted = StructuredOutputParser.extractJsonString(raw);
        assertThat(extracted).isEqualTo("{\"title\":\"Test\",\"count\":5,\"active\":true}");
    }

    @Test
    @DisplayName("Extracts JSON bounded by braces even without code block")
    void testExtractJsonBoundedByBraces() {
        String raw = "Sure! {\"title\":\"Test\",\"count\":5,\"active\":true} is the requested data.";
        String extracted = StructuredOutputParser.extractJsonString(raw);
        assertThat(extracted).isEqualTo("{\"title\":\"Test\",\"count\":5,\"active\":true}");
    }

    @Test
    @DisplayName("Parses JSON into JsonNode correctly")
    void testParseJsonNode() {
        String raw = "```json\n{\"title\":\"Item\",\"count\":10}\n```";
        JsonNode node = StructuredOutputParser.parseJsonNode(raw);
        assertThat(node.get("title").asText()).isEqualTo("Item");
        assertThat(node.get("count").asInt()).isEqualTo(10);
    }

    @Test
    @DisplayName("Parses JSON into typed Record object")
    void testParseObject() {
        String raw = "```json\n{\"title\":\"Item\",\"count\":42,\"active\":true}\n```";
        SampleResult obj = StructuredOutputParser.parseObject(raw, SampleResult.class);
        assertThat(obj.title()).isEqualTo("Item");
        assertThat(obj.count()).isEqualTo(42);
        assertThat(obj.active()).isTrue();
    }

    @Test
    @DisplayName("Throws StructuredOutputException on invalid JSON")
    void testInvalidJsonThrows() {
        String raw = "Not valid JSON at all";
        assertThatThrownBy(() -> StructuredOutputParser.parseJsonNode(raw))
                .isInstanceOf(StructuredOutputException.class);
    }

    @Test
    @DisplayName("Throws StructuredOutputException on empty output")
    void testEmptyOutputThrows() {
        assertThatThrownBy(() -> StructuredOutputParser.extractJsonString(""))
                .isInstanceOf(StructuredOutputException.class);
    }
}
