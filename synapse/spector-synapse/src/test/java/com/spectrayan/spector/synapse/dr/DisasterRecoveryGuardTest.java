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
package com.spectrayan.spector.synapse.dr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.config.SpectorPropertyConstants;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceLegalHoldException;
import com.spectrayan.spector.synapse.catalog.file.FileAccountCatalog;
import com.spectrayan.spector.synapse.config.dr.DisasterRecoveryProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Architectural guards and baseline invariants for Cell Disaster Recovery (Phase 6, Group 0).
 *
 * <ul>
 *   <li>Task 0.3 / Req R10.1: DR export defaults to OFF, reflectively pinned against inlining.</li>
 *   <li>Task 0.5 / Req R6.7, V8: Legal hold strictly blocks tombstone via NamespaceLegalHoldException.</li>
 *   <li>Task 0.6 / Req R6.4, R6.5: NamespaceRecord field list verified to lack tenantId, bounding erasure completeness.</li>
 * </ul>
 */
@DisplayName("Phase 6 Group 0: Safety Nets, Architectural Guards, and Honest Documentation")
class DisasterRecoveryGuardTest {

    @Test
    @DisplayName("Task 0.3 / R10.1: DR export must default to OFF (pinned reflectively)")
    void testDrExportDefaultsOffReflectively() throws Exception {
        Field field = SpectorPropertyConstants.class.getField("DEFAULT_DR_EXPORT_ENABLED");
        boolean defaultValue = (boolean) field.get(null);
        assertThat(defaultValue)
                .describedAs("DEFAULT_DR_EXPORT_ENABLED must be false by default")
                .isFalse();

        DisasterRecoveryProperties props = new DisasterRecoveryProperties();
        assertThat(props.isExportEnabled())
                .describedAs("DisasterRecoveryProperties.isExportEnabled() must default to false")
                .isFalse();
    }

    @Test
    @DisplayName("Task 0.5 / R6.7, V8: Legal hold strictly blocks tombstone via NamespaceLegalHoldException")
    void testLegalHoldBlocksTombstone(@TempDir Path tempDir) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        FileAccountCatalog catalog = new FileAccountCatalog(tempDir, objectMapper);

        String accountId = "acc-legal-01";
        catalog.getOrCreateAccount(accountId);

        NamespaceRecord ns = catalog.createNamespace(accountId, "evidence-vault", NamespaceType.PROJECT);
        assertThat(ns.legalHold()).isFalse();

        // Enable legal hold
        NamespaceRecord held = catalog.setLegalHold(accountId, "evidence-vault", true);
        assertThat(held.legalHold()).isTrue();

        assertThatThrownBy(() -> catalog.tombstone(accountId, held.namespaceId()))
                .isInstanceOf(NamespaceLegalHoldException.class)
                .hasMessageContaining(held.namespaceId());
    }

    @Test
    @DisplayName("Task 0.6 / R6.4, R6.5: NamespaceRecord field inventory verified — tenantId is absent")
    void testNamespaceRecordFieldInventoryLacksTenantId() {
        RecordComponent[] components = NamespaceRecord.class.getRecordComponents();
        Set<String> fieldNames = Arrays.stream(components)
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());

        Set<String> expectedFields = Set.of(
                "namespaceId",
                "slug",
                "ownerAccountId",
                "type",
                "status",
                "displayName",
                "description",
                "bias",
                "createdAt",
                "lastAccessedAt",
                "legalHold"
        );

        assertThat(fieldNames)
                .as("NamespaceRecord components must match verified list exactly")
                .containsExactlyInAnyOrderElementsOf(expectedFields);

        assertThat(fieldNames)
                .as("NamespaceRecord does NOT contain tenantId; erasure cannot claim completeness across unmapped accounts")
                .doesNotContain("tenantId");
    }
}
