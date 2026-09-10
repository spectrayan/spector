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
package com.spectrayan.spector.memory;
import com.spectrayan.spector.kernel.score.EdgeImportance;

import com.spectrayan.spector.config.SpectorProperties;
import com.spectrayan.spector.config.properties.MemoryProperties;
import com.spectrayan.spector.config.properties.ProviderProperties;
import com.spectrayan.spector.memory.model.MemoryPersistenceMode;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.embedding.EmbeddingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture guardrail test enforcing Issue #759:
 * SpectorMemoryBuilder slim-down, dynamic configuration duplication ban,
 * snapshot immutability, and data twin collapse.
 */
@DisplayName("DuplicateConfigBanTest: Architecture Guardrails for Issue #759")
class DuplicateConfigBanTest {

    @Test
    @DisplayName("SpectorMemoryBuilder must not declare scalar setters matching any property on MemoryProperties")
    void testNoDuplicateScalarSettersOnMemoryBuilder() {
        // Dynamically find all non-static fields declared on MemoryProperties
        Set<String> memoryFieldNames = Arrays.stream(MemoryProperties.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .map(Field::getName)
                .collect(Collectors.toSet());

        // Also extract property names from all getters (getFoo -> foo, isBar -> bar)
        Set<String> memoryGetterNames = Arrays.stream(MemoryProperties.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers()))
                .filter(m -> m.getParameterCount() == 0)
                .map(Method::getName)
                .map(name -> {
                    if (name.startsWith("get") && name.length() > 3 && Character.isUpperCase(name.charAt(3))) {
                        return Character.toLowerCase(name.charAt(3)) + name.substring(4);
                    } else if (name.startsWith("is") && name.length() > 2 && Character.isUpperCase(name.charAt(2))) {
                        return Character.toLowerCase(name.charAt(2)) + name.substring(3);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> allMemoryPropertyNames = new HashSet<>(memoryFieldNames);
        allMemoryPropertyNames.addAll(memoryGetterNames);

        // Allowed collaborator/instance coordinate setter names on SpectorMemoryBuilder:
        // - edgeImportance: takes EdgeImportance domain enum collaborator, not String
        // - tagExtractor: takes TagExtractor domain collaborator interface, not TagExtractorMode enum
        // - persistenceMode: sets instance storage layout coordinate
        // - namespaceId: sets instance namespace coordinate
        // - bundleMode: sets instance storage packing flag
        Set<String> allowedInstanceMembers = Set.of(
                "persistenceMode",
                "namespaceId",
                "bundleMode",
                "edgeImportance",
                "tagExtractor"
        );

        Set<String> targetPropertiesToCheck = allMemoryPropertyNames.stream()
                .filter(name -> !allowedInstanceMembers.contains(name))
                .collect(Collectors.toSet());

        // Find all public single-argument setter methods on SpectorMemoryBuilder
        Set<String> builderSetterNames = Arrays.stream(SpectorMemoryBuilder.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> m.getParameterCount() == 1)
                .map(Method::getName)
                .collect(Collectors.toSet());

        List<String> duplicateSetters = targetPropertiesToCheck.stream()
                .filter(builderSetterNames::contains)
                .sorted()
                .toList();

        assertThat(duplicateSetters)
                .as("SpectorMemoryBuilder must not declare scalar setters matching MemoryProperties fields/getters. " +
                        "Configure these properties on SpectorProperties or MemoryProperties instead.")
                .isEmpty();
    }


    @Test
    @DisplayName("SpectorMemoryBuilder.build() and mutations must not mutate caller's SpectorProperties snapshot")
    void testSnapshotImmutabilityOnBuild() {
        MemoryProperties memProps = new MemoryProperties();
        memProps.setDimensions(384);
        memProps.setWorkingCapacity(50);
        memProps.setEpisodicPartitionCapacity(100);
        memProps.setSemanticCapacity(100);
        memProps.setProceduralCapacity(100);

        ProviderProperties provProps = new ProviderProperties();
        provProps.getEmbedding().setBatchSize(16);

        SpectorProperties original = SpectorProperties.of(memProps, provProps);

        int initialDims = original.memory().getDimensions();
        int initialWorkingCap = original.memory().getWorkingCapacity();
        int initialBatchSize = original.provider().getEmbedding().getBatchSize();

        SpectorMemoryBuilder builder = SpectorMemoryBuilder.createEmpty()
                .fromProperties(original)
                .embeddingProvider(new MockEmbeddingProvider(768))
                .persistenceMode(MemoryPersistenceMode.IN_MEMORY);

        // Call build() which infers dimensions from embeddingProvider (768)
        try (SpectorMemory memory = builder.build()) {
            assertThat(memory).isNotNull();

            // Verify original snapshot was NOT mutated by build() dimension inference
            assertThat(original.memory().getDimensions())
                    .as("build() dimension inference must not mutate caller's MemoryProperties dimensions")
                    .isEqualTo(initialDims);
            assertThat(original.memory().getWorkingCapacity())
                    .as("build() must not mutate caller's MemoryProperties workingCapacity")
                    .isEqualTo(initialWorkingCap);
            assertThat(original.provider().getEmbedding().getBatchSize())
                    .as("build() must not mutate caller's ProviderProperties batchSize")
                    .isEqualTo(initialBatchSize);
        }
    }

    @Test
    @DisplayName("SpectorMemoryBuilder method count must remain bounded (assembly object, not config store)")
    void testBuilderMethodCountBounded() {
        long publicMethodCount = Arrays.stream(SpectorMemoryBuilder.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .count();

        // Pre-refactor: ~193 public members. Target: <= 110 public methods (collaborators + coordinates + lifecycle).
        assertThat(publicMethodCount)
                .as("SpectorMemoryBuilder public method count must not bloat back to pre-#758 levels (~193)")
                .isLessThanOrEqualTo(110);
    }

    private static class MockEmbeddingProvider implements EmbeddingProvider {
        private final int dims;

        MockEmbeddingProvider(int dims) {
            this.dims = dims;
        }

        @Override
        public EmbeddingResult embed(String text) {
            return new EmbeddingResult(new float[dims], 1, "mock");
        }

        @Override
        public int dimensions() {
            return dims;
        }

        @Override
        public String modelName() {
            return "mock";
        }
    }
}
