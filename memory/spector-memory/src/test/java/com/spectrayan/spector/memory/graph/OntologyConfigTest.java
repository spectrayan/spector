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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OntologyConfigTest {

    @Test
    @DisplayName("defaultInstance — returns thread-safe non-null singleton instance")
    void defaultInstanceIsSingleton() {
        OntologyConfig instance1 = OntologyConfig.defaultInstance();
        OntologyConfig instance2 = OntologyConfig.defaultInstance();

        assertThat(instance1).isNotNull();
        assertThat(instance2).isSameAs(instance1);
    }

    @Test
    @DisplayName("entityTypes — resolves canonical types, aliases, and parent hierarchies")
    void entityTypesResolution() {
        OntologyConfig config = OntologyConfig.defaultInstance();

        assertThat(config.isKnownType("PERSON")).isTrue();
        assertThat(config.isKnownType("PROJECT")).isTrue();
        assertThat(config.isKnownType("EVENT")).isTrue();

        // Alias resolution
        assertThat(config.resolveType("REPO")).contains("PROJECT");
        assertThat(config.resolveType("REPOSITORY")).contains("PROJECT");
        assertThat(config.resolveType("CODEBASE")).contains("PROJECT");
        assertThat(config.resolveType("MICROSERVICE")).contains("SERVICE");
        assertThat(config.resolveType("SQUAD")).contains("TEAM");

        // Hierarchy
        assertThat(config.parentType("TEAM")).contains("ORGANIZATION");
        assertThat(config.parentType("PROGRAMMING_LANGUAGE")).contains("TECHNOLOGY");
        assertThat(config.areMergeCompatible("TEAM", "ORGANIZATION")).isTrue();
    }

    @Test
    @DisplayName("relationshipPredicates — resolves causal predicates, aliases, and inverses (ADR-0010)")
    void causalRelationshipPredicatesResolution() {
        OntologyConfig config = OntologyConfig.defaultInstance();

        // Direct canonical predicates
        assertThat(config.isKnownPredicate("CAUSES")).isTrue();
        assertThat(config.isKnownPredicate("CAUSED_BY")).isTrue();
        assertThat(config.isKnownPredicate("LED_TO")).isTrue();
        assertThat(config.isKnownPredicate("TRIGGERED")).isTrue();
        assertThat(config.isKnownPredicate("PREVENTED")).isTrue();
        assertThat(config.isKnownPredicate("CORRELATED_WITH")).isTrue();

        // Alias resolution
        assertThat(config.resolvePredicate("INDUCES")).contains("CAUSES");
        assertThat(config.resolvePredicate("RESULTS_IN")).contains("CAUSES");
        assertThat(config.resolvePredicate("DUE_TO")).contains("CAUSED_BY");
        assertThat(config.resolvePredicate("ACTIVATED_BY")).contains("TRIGGERED_BY");
        assertThat(config.resolvePredicate("BLOCKED")).contains("PREVENTED");

        // Inverses
        assertThat(config.inversePredicate("CAUSES")).contains("CAUSED_BY");
        assertThat(config.inversePredicate("CAUSED_BY")).contains("CAUSES");
        assertThat(config.inversePredicate("LED_TO")).contains("RESULTED_FROM");
        assertThat(config.inversePredicate("TRIGGERED")).contains("TRIGGERED_BY");
        assertThat(config.inversePredicate("PREVENTED")).contains("PREVENTED_BY");
        assertThat(config.inversePredicate("PRECEDES")).contains("FOLLOWS");
        assertThat(config.inversePredicate("FOLLOWS")).contains("PRECEDES");
    }
}
