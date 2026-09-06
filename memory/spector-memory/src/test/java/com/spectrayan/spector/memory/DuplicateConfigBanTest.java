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

import com.spectrayan.spector.memory.aisme.config.AismeConfig;
import com.spectrayan.spector.memory.pathway.dream.relay.DreamConfig;
import com.spectrayan.spector.memory.pathway.reflect.daemon.CircadianPolicy;
import com.spectrayan.spector.memory.synapse.DecayConfig;
import com.spectrayan.spector.memory.synapse.TwoFactorConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Architecture guardrail test enforcing Issue #759:
 * SpectorMemoryBuilder slim-down and data twin collapse.
 *
 * <p>Verifies that:
 * <ol>
 *   <li>{@link SpectorMemoryBuilder} does not re-introduce scalar configuration setters
 *       that duplicate properties already on {@link com.spectrayan.spector.config.SpectorProperties}
 *       or {@link com.spectrayan.spector.config.properties.MemoryProperties}.</li>
 *   <li>Legacy data twin classes ({@link DreamConfig}, {@link CircadianPolicy},
 *       {@link TwoFactorConfig}, {@link AismeConfig}, {@link DecayConfig}) remain marked with
 *       {@link Deprecated}.</li>
 * </ol>
 */
@DisplayName("DuplicateConfigBanTest: Architecture Guardrails for Issue #759")
class DuplicateConfigBanTest {

    /**
     * Scalar setters that belong exclusively on {@link com.spectrayan.spector.config.properties.MemoryProperties}
     * or other {@code spector-config} POJOs, and must NEVER be declared as fluent scalar setters on the builder.
     */
    private static final Set<String> BANNED_SCALAR_SETTERS = Set.of(
            "workingCapacity",
            "textSegmentSize",
            "episodicSegmentSize",
            "semanticCapacity",
            "proceduralCapacity",
            "entityGraphCapacity",
            "hebbianGraphCapacity",
            "temporalChainCapacity",
            "nodesPerPartition",
            "checkpointIntervalSeconds",
            "inhibitionTtlMs",
            "inhibitionFloor",
            "deduplicationRadius",
            "surpriseWarmup",
            "flashbulbThreshold",
            "valenceLearningRate",
            "temporalRetentionDays",
            "hebbianMaxDegree",
            "entityMaxDegree",
            "maxEntitiesPerMemory",
            "maxRelationsPerMemory",
            "coactivationPairCapacity",
            "coactivationEdgeCapacity",
            "temporalFactsInitialSize",
            "indexMidxCapacity",
            "indexIdplSize",
            "typeRegistryCapacity",
            "typeRegistrySize",
            "insulaSize",
            "provenanceCapacity",
            "entityExtractionParallelism",
            "entityExtractionQueueCapacity",
            "eagerConsolidationQueueCapacity",
            "dimensions",
            "capacity",
            "pinSourceEpisodes",
            "pinnedQuota",
            "persistWorkingMemory",
            "entityResolutionEnabled",
            "entityShadowMode",
            "entityCosineThreshold",
            "enableMmr",
            "mmrLambda",
            "schedulerEnabled",
            "wanderEnabled",
            "dreamEnabled",
            "maxNamespaces"
    );

    @Test
    @DisplayName("SpectorMemoryBuilder must not declare any banned scalar setters")
    void testNoDuplicateScalarSettersOnMemoryBuilder() {
        Set<String> declaredMethodNames = Arrays.stream(SpectorMemoryBuilder.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .collect(Collectors.toSet());

        List<String> violations = BANNED_SCALAR_SETTERS.stream()
                .filter(declaredMethodNames::contains)
                .sorted()
                .toList();

        assertThat(violations)
                .as("SpectorMemoryBuilder must not declare scalar setters that duplicate SpectorProperties/MemoryProperties. " +
                        "Configure these properties on SpectorProperties or MemoryProperties and pass them to SpectorMemory.builder(props).")
                .isEmpty();
    }

    @Test
    @DisplayName("Legacy data twin classes must remain marked with @Deprecated")
    void testLegacyDataTwinsAreDeprecated() {
        List<Class<?>> dataTwins = List.of(
                DreamConfig.class,
                CircadianPolicy.class,
                TwoFactorConfig.class,
                AismeConfig.class,
                DecayConfig.class
        );

        for (Class<?> clazz : dataTwins) {
            assertThat(clazz.isAnnotationPresent(Deprecated.class))
                    .as("Data twin class %s must be annotated with @Deprecated to prevent new usages", clazz.getName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("SpectorMemoryBuilder method count must remain bounded (assembly object, not config store)")
    void testBuilderMethodCountBounded() {
        long publicMethodCount = Arrays.stream(SpectorMemoryBuilder.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .count();

        // Pre-refactor: ~193 public members. Target: <= 100 public methods (collaborators + coordinates + lifecycle).
        assertThat(publicMethodCount)
                .as("SpectorMemoryBuilder public method count must not bloat back to pre-#758 levels (~193)")
                .isLessThanOrEqualTo(100);
    }
}
