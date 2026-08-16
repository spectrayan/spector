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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemaGeneratorTest {

    enum Status {
        ACTIVE, INACTIVE, PENDING
    }

    record Address(String street, String city, int zipCode) {}

    record UserProfile(
            String name,
            int age,
            boolean verified,
            Status status,
            List<String> tags,
            Address address,
            Map<String, String> metadata
    ) {}

    @Test
    @DisplayName("Generate JSON Schema from record produces expected JSON schema structure")
    void testGenerateSchemaFromRecord() {
        JsonNode schema = JsonSchemaGenerator.generateSchemaNode(UserProfile.class);

        assertThat(schema.get("$schema").asText()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
        assertThat(schema.get("title").asText()).isEqualTo("UserProfile");
        assertThat(schema.get("type").asText()).isEqualTo("object");

        JsonNode props = schema.get("properties");
        assertThat(props.get("name").get("type").asText()).isEqualTo("string");
        assertThat(props.get("age").get("type").asText()).isEqualTo("integer");
        assertThat(props.get("verified").get("type").asText()).isEqualTo("boolean");

        // Enum verification
        assertThat(props.get("status").get("type").asText()).isEqualTo("string");
        assertThat(props.get("status").get("enum")).hasSize(3);

        // List verification
        assertThat(props.get("tags").get("type").asText()).isEqualTo("array");
        assertThat(props.get("tags").get("items").get("type").asText()).isEqualTo("string");

        // Nested record verification
        assertThat(props.get("address").get("type").asText()).isEqualTo("object");
        assertThat(props.get("address").get("properties").get("city").get("type").asText()).isEqualTo("string");

        // Map verification
        assertThat(props.get("metadata").get("type").asText()).isEqualTo("object");

        // Required fields
        JsonNode required = schema.get("required");
        assertThat(required).isNotNull();
        assertThat(required.toString()).contains("name", "age", "verified", "status", "tags", "address", "metadata");
    }

    @Test
    @DisplayName("Generate schema JSON string formatted or compact")
    void testGenerateSchemaJsonString() {
        String compact = JsonSchemaGenerator.generateSchemaJson(Address.class, false);
        String pretty = JsonSchemaGenerator.generateSchemaJson(Address.class, true);

        assertThat(compact).contains("\"title\":\"Address\"");
        assertThat(pretty).contains("\n");
    }
}
