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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.kernel.id.SystemMemoryId;
import com.spectrayan.spector.kernel.store.TypeRegistryMemory;
import com.spectrayan.spector.memory.graph.temporal.TemporalKnowledgeGraph;

class DictionaryEntityExtractorTest {

    private EntityDirectory entityDirectory;
    private TemporalKnowledgeGraph temporalKnowledgeGraph;
    private OntologyConfig ontologyConfig;
    private DictionaryEntityExtractor extractor;

    @BeforeEach
    void setUp() {
        ontologyConfig = OntologyConfig.defaultInstance();
        String[] seedTypes = ontologyConfig.canonicalTypes().toArray(String[]::new);
        TypeRegistryMemory entityTypeReg = TypeRegistryMemory.seeded(SystemMemoryId.ENTITY_TYPE, seedTypes);
        entityDirectory = new EntityDirectory(100, entityTypeReg);

        TypeRegistryMemory predReg = new TypeRegistryMemory(SystemMemoryId.RELATION_TYPE);
        temporalKnowledgeGraph = new TemporalKnowledgeGraph(predReg);

        extractor = new DictionaryEntityExtractor(entityDirectory, temporalKnowledgeGraph, ontologyConfig, 10);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (entityDirectory != null) {
            entityDirectory.close();
        }
        if (temporalKnowledgeGraph != null) {
            temporalKnowledgeGraph.close();
        }
    }

    @Test
    @DisplayName("Extracts entities matching known entities in EntityDirectory")
    void extractsInternedEntities() {
        entityDirectory.intern("spector", "PROJECT");
        entityDirectory.intern("alice", "PERSON");

        List<ExtractedEntity> entities = extractor.extract("mem-1", "Alice completed the architecture design for spector.");
        assertThat(entities).isNotEmpty();

        List<String> names = entities.stream().map(ExtractedEntity::name).toList();
        assertThat(names).contains("alice", "spector");
    }

    @Test
    @DisplayName("Extracts entities matching canonical ontology types and aliases")
    void extractsOntologyTypes() {
        List<ExtractedEntity> entities = extractor.extract("mem-2", "The developer configured the database and checked the microservice.");
        assertThat(entities).isNotEmpty();

        List<String> types = entities.stream().map(ExtractedEntity::typeName).toList();
        // DEVELOPER resolves to PERSON, DATABASE is DATABASE, MICROSERVICE resolves to SERVICE
        assertThat(types).contains("PERSON", "DATABASE");
    }

    @Test
    @DisplayName("Attaches TKG relations when facts exist between co-occurring entities")
    void attachesTkgRelations() {
        int aliceId = entityDirectory.intern("alice", "PERSON");
        int spectorId = entityDirectory.intern("spector", "PROJECT");

        long nowSec = System.currentTimeMillis() / 1000L;
        temporalKnowledgeGraph.assertFact(aliceId, "WORKS_ON", spectorId, -1L, (short) 0, nowSec, Long.MAX_VALUE, 0.95f, false);

        List<ExtractedEntity> entities = extractor.extract("mem-3", "Alice works on spector daily.");
        assertThat(entities).hasSize(2);

        ExtractedEntity alice = entities.stream().filter(e -> e.name().equals("alice")).findFirst().orElseThrow();
        assertThat(alice.relations()).isNotEmpty();
        assertThat(alice.relations().getFirst().targetEntityName()).isEqualTo("spector");
        assertThat(alice.relations().getFirst().relationTypeName()).isEqualTo("WORKS_ON");
    }

    @Test
    @DisplayName("Ignores common stopwords loaded from external resource")
    void ignoresStopwords() {
        List<ExtractedEntity> entities = extractor.extract("mem-4", "the and for with this that from have been will");
        assertThat(entities).isEmpty();
    }
}
