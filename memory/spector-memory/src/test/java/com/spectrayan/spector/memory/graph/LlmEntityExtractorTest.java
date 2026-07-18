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

import com.spectrayan.spector.provider.generation.GenerationOptions;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.provider.model.LlmRequest;
import com.spectrayan.spector.provider.model.LlmResponse;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for LlmEntityExtractor response parsing and error handling.
 */
class LlmEntityExtractorTest {

    @Test
    void parsesSimpleResponse() {
        String response = """
                ENTITY: Alice | PERSON
                ENTITY: Project Alpha | PROJECT
                RELATION: Alice | MANAGES | Project Alpha
                """;

        LlmProvider mockProvider = mockLlm(response, true);

        LlmEntityExtractor extractor = new LlmEntityExtractor(mockProvider);
        List<ExtractedEntity> entities = extractor.extract("test-id", "Alice manages Project Alpha");

        assertThat(entities).hasSize(2);
        assertThat(entities.get(0).name()).isEqualTo("Alice");
        assertThat(entities.get(0).type()).isEqualTo("PERSON");
        assertThat(entities.get(0).relations()).hasSize(1);
        assertThat(entities.get(0).relations().get(0).relationType()).isEqualTo("MANAGES");
        assertThat(entities.get(0).relations().get(0).targetEntityName()).isEqualTo("Project Alpha");

        assertThat(entities.get(1).name()).isEqualTo("Project Alpha");
        assertThat(entities.get(1).type()).isEqualTo("PROJECT");
    }

    @Test
    void handlesEmptyResponse() {
        LlmProvider mockProvider = mockLlm("", true);

        LlmEntityExtractor extractor = new LlmEntityExtractor(mockProvider);
        List<ExtractedEntity> entities = extractor.extract("test-id", "some text");

        assertThat(entities).isEmpty();
    }

    @Test
    void handlesNullProvider() {
        LlmEntityExtractor extractor = new LlmEntityExtractor(null);

        assertThat(extractor.isAvailable()).isFalse();
        assertThat(extractor.extract("test", "text")).isEmpty();
    }

    @Test
    void handlesUnavailableProvider() {
        LlmProvider mockProvider = mockLlm("Should not be called", false);

        LlmEntityExtractor extractor = new LlmEntityExtractor(mockProvider);
        assertThat(extractor.extract("test", "text")).isEmpty();
    }

    @Test
    void handlesProviderException() {
        LlmProvider mockProvider = mockLlm(null, true);

        LlmEntityExtractor extractor = new LlmEntityExtractor(mockProvider);
        List<ExtractedEntity> entities = extractor.extract("test-id", "text");

        assertThat(entities).isEmpty(); // graceful degradation
    }

    @Test
    void preservesNovelEntityType() {
        String response = "ENTITY: Widget | GADGET\n";

        LlmProvider mockProvider = mockLlm(response, true);

        LlmEntityExtractor extractor = new LlmEntityExtractor(mockProvider);
        List<ExtractedEntity> entities = extractor.extract("test", "text");

        assertThat(entities).hasSize(1);
        // Open-schema: novel types are preserved, NOT collapsed to OTHER
        assertThat(entities.get(0).type()).isEqualTo("GADGET");
        assertThat(entities.get(0).typeName()).isEqualTo("GADGET");
    }

    @Test
    void respectsMaxEntitiesLimit() {
        StringBuilder response = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            response.append("ENTITY: Entity" + i + " | PERSON\n");
        }

        LlmProvider mockProvider = mockLlm(response.toString(), true);

        LlmEntityExtractor extractor = new LlmEntityExtractor(mockProvider, 5, 10);
        List<ExtractedEntity> entities = extractor.extract("test", "text");

        assertThat(entities).hasSize(5);
    }

    @Test
    void multipleRelationsForSameEntity() {
        String response = """
                ENTITY: Alice | PERSON
                ENTITY: Bob | PERSON
                ENTITY: Acme | ORGANIZATION
                RELATION: Alice | MANAGES | Bob
                RELATION: Alice | WORKS_ON | Acme
                """;

        LlmProvider mockProvider = mockLlm(response, true);

        LlmEntityExtractor extractor = new LlmEntityExtractor(mockProvider);
        List<ExtractedEntity> entities = extractor.extract("test", "text");

        assertThat(entities).hasSize(3);
        // Alice should have 2 relations
        var alice = entities.get(0);
        assertThat(alice.relations()).hasSize(2);
    }

    @Test
    void normalizesHyphenatedRelationType() {
        String response = """
                ENTITY: Service A | TECHNOLOGY
                ENTITY: Library B | TECHNOLOGY
                RELATION: Service A | DEPENDS-ON | Library B
                """;

        LlmProvider mockProvider = mockLlm(response, true);

        LlmEntityExtractor extractor = new LlmEntityExtractor(mockProvider);
        List<ExtractedEntity> entities = extractor.extract("test", "text");

        assertThat(entities).hasSize(2);
        var serviceA = entities.get(0);
        assertThat(serviceA.relations()).hasSize(1);
        assertThat(serviceA.relations().get(0).relationType()).isEqualTo("DEPENDS_ON");
    }

    @Test
    void normalizesSpaceSeparatedRelationType() {
        String response = """
                ENTITY: Alice | PERSON
                ENTITY: Project X | PROJECT
                RELATION: Alice | WORKS ON | Project X
                """;

        LlmProvider mockProvider = mockLlm(response, true);

        LlmEntityExtractor extractor = new LlmEntityExtractor(mockProvider);
        List<ExtractedEntity> entities = extractor.extract("test", "text");

        assertThat(entities).hasSize(2);
        var alice = entities.get(0);
        assertThat(alice.relations()).hasSize(1);
        assertThat(alice.relations().get(0).relationType()).isEqualTo("WORKS_ON");
    }

    @Test
    void normalizesMixedSeparatorsInRelationType() {
        String response = """
                ENTITY: Module A | TECHNOLOGY
                ENTITY: Module B | TECHNOLOGY
                ENTITY: Module C | TECHNOLOGY
                RELATION: Module A | PART_OF | Module B
                RELATION: Module B | DEPENDS-ON | Module C
                """;

        LlmProvider mockProvider = mockLlm(response, true);

        LlmEntityExtractor extractor = new LlmEntityExtractor(mockProvider);
        List<ExtractedEntity> entities = extractor.extract("test", "text");

        assertThat(entities).hasSize(3);
        // Module A: PART_OF (already correct)
        assertThat(entities.get(0).relations()).hasSize(1);
        assertThat(entities.get(0).relations().get(0).relationType()).isEqualTo("PART_OF");
        // Module B: DEPENDS-ON → normalized to DEPENDS_ON
        assertThat(entities.get(1).relations()).hasSize(1);
        assertThat(entities.get(1).relations().get(0).relationType()).isEqualTo("DEPENDS_ON");
    }

    @Test
    void handlesSwappedRelationFormat() {
        // Some small models output: RELATION: REL_TYPE | entity1 | entity2
        // instead of the expected:  RELATION: entity1 | REL_TYPE | entity2
        String response = """
                ENTITY: Alex | PERSON
                ENTITY: Blake | PERSON
                RELATION: DEPENDS_ON | Alex | Blake
                RELATION: ASSIGNED_TO | Blake | Alex
                """;

        LlmProvider mockProvider = mockLlm(response, true);

        LlmEntityExtractor extractor = new LlmEntityExtractor(mockProvider);
        List<ExtractedEntity> entities = extractor.extract("test", "text");

        assertThat(entities).hasSize(2);
        // Alex should be the source of DEPENDS_ON (after swap detection)
        var alex = entities.get(0);
        assertThat(alex.relations()).hasSize(1);
        assertThat(alex.relations().get(0).relationType()).isEqualTo("DEPENDS_ON");
        assertThat(alex.relations().get(0).targetEntityName()).isEqualTo("Blake");

        // Blake should be the source of ASSIGNED_TO (after swap detection)
        var blake = entities.get(1);
        assertThat(blake.relations()).hasSize(1);
        assertThat(blake.relations().get(0).relationType()).isEqualTo("ASSIGNED_TO");
        assertThat(blake.relations().get(0).targetEntityName()).isEqualTo("Alex");
    }

    private LlmProvider mockLlm(String fixedResponse, boolean available) {
        return new LlmProvider() {
            @Override
            public LlmResponse generate(LlmRequest request, GenerationOptions options) {
                if (!available) {
                    throw new RuntimeException("Should not be called");
                }
                if (fixedResponse == null) {
                    throw new RuntimeException("API failure");
                }
                return new LlmResponse(fixedResponse, 0, 0, "test-mock");
            }
            @Override
            public boolean isAvailable() { return available; }
            @Override
            public String modelName() { return "test-mock"; }
        };
    }
}
