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
package com.spectrayan.spector.synapse.catalog.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spectrayan.spector.memory.kernel.StorageLayout;
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.catalog.NamespaceBias;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.exception.DefaultNamespaceProtectedException;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceNotFoundException;

@DisplayName("FileAccountCatalog Specifications")
class FileAccountCatalogTest {

    @TempDir
    Path tempDir;

    private FileAccountCatalog catalog;
    private ObjectMapper objectMapper;

    private static final String ACCOUNT_ID = "0195500000001";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        catalog = new FileAccountCatalog(tempDir, objectMapper);
    }

    @Test
    @DisplayName("getOrCreateAccount initializes default namespace with rich record")
    void testGetOrCreateAccount() {
        Account account = catalog.getOrCreateAccount(ACCOUNT_ID);
        assertThat(account.id()).isEqualTo(ACCOUNT_ID);
        assertThat(account.defaultNamespaceId()).isEqualTo(ACCOUNT_ID);

        Optional<NamespaceRecord> defaultNs = catalog.resolve(ACCOUNT_ID, "default");
        assertThat(defaultNs).isPresent();
        assertThat(defaultNs.get().slug()).isEqualTo("default");
        assertThat(defaultNs.get().namespaceId()).isEqualTo(ACCOUNT_ID);
        assertThat(defaultNs.get().type()).isEqualTo(NamespaceType.DEFAULT);
        assertThat(defaultNs.get().status()).isEqualTo(NamespaceStatus.ACTIVE);
    }

    @Test
    @DisplayName("createNamespace creates and persists rich metadata")
    void testCreateNamespaceWithMetadata() {
        catalog.getOrCreateAccount(ACCOUNT_ID);

        var bias = new NamespaceBias(List.of("biology", "neuroscience"), Map.of("brain", 1.5f));
        NamespaceRecord created = catalog.createNamespace(
                ACCOUNT_ID, "research-ns", NamespaceType.PROJECT,
                "Research Namespace", "Contains neuroscience research", bias
        );

        assertThat(created.slug()).isEqualTo("research-ns");
        assertThat(created.displayName()).isEqualTo("Research Namespace");
        assertThat(created.description()).isEqualTo("Contains neuroscience research");
        assertThat(created.bias()).isNotNull();
        assertThat(created.bias().domainFocus()).contains("biology", "neuroscience");

        // Verify resolved
        Optional<NamespaceRecord> resolved = catalog.resolve(ACCOUNT_ID, "research-ns");
        assertThat(resolved).isPresent();
        assertThat(resolved.get().namespaceId()).isEqualTo(created.namespaceId());
        assertThat(resolved.get().displayName()).isEqualTo("Research Namespace");
    }

    @Test
    @DisplayName("createNamespace validates slug grammar")
    void testSlugGrammarValidation() {
        catalog.getOrCreateAccount(ACCOUNT_ID);

        assertThatThrownBy(() -> catalog.createNamespace(ACCOUNT_ID, "invalid/slug", NamespaceType.PROJECT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid namespace slug");

        assertThatThrownBy(() -> catalog.createNamespace(ACCOUNT_ID, ".hidden", NamespaceType.PROJECT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid namespace slug");

        assertThatThrownBy(() -> catalog.createNamespace(ACCOUNT_ID, "", NamespaceType.PROJECT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid namespace slug");
    }

    @Test
    @DisplayName("updateNamespace updates mutable fields")
    void testUpdateNamespace() {
        catalog.getOrCreateAccount(ACCOUNT_ID);
        NamespaceRecord created = catalog.createNamespace(ACCOUNT_ID, "my-slug", NamespaceType.PROJECT);

        var newBias = new NamespaceBias(List.of("code"), Map.of("java", 2.0f));
        NamespaceRecord updated = catalog.updateNamespace(
                ACCOUNT_ID, "my-slug", "New Name", "New Description",
                NamespaceType.AGENT, newBias
        );

        assertThat(updated.displayName()).isEqualTo("New Name");
        assertThat(updated.description()).isEqualTo("New Description");
        assertThat(updated.type()).isEqualTo(NamespaceType.AGENT);
        assertThat(updated.bias()).isEqualTo(newBias);

        // Re-resolve
        Optional<NamespaceRecord> resolved = catalog.resolve(ACCOUNT_ID, "my-slug");
        assertThat(resolved).isPresent();
        assertThat(resolved.get().displayName()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("updateNamespace throws NamespaceNotFoundException for missing slug")
    void testUpdateNamespaceNotFound() {
        catalog.getOrCreateAccount(ACCOUNT_ID);

        assertThatThrownBy(() -> catalog.updateNamespace(
                ACCOUNT_ID, "missing", "Name", null, null, null
        )).isInstanceOf(NamespaceNotFoundException.class);
    }

    @Test
    @DisplayName("resetNamespace clears data directory")
    void testResetNamespace() throws IOException {
        catalog.getOrCreateAccount(ACCOUNT_ID);
        NamespaceRecord created = catalog.createNamespace(ACCOUNT_ID, "reset-target", NamespaceType.PROJECT);

        Path nsDir = StorageLayout.namespaceDirSharded(tempDir, created.namespaceId());
        Path dummyFile = nsDir.resolve("dummy.txt");
        Files.writeString(dummyFile, "test data");
        assertThat(Files.exists(dummyFile)).isTrue();

        catalog.resetNamespace(ACCOUNT_ID, "reset-target");

        assertThat(Files.exists(dummyFile)).isFalse();
        assertThat(Files.exists(nsDir)).isTrue();
    }

    @Test
    @DisplayName("tombstone soft-deletes namespace and protects default")
    void testTombstoneAndProtection() {
        catalog.getOrCreateAccount(ACCOUNT_ID);
        NamespaceRecord created = catalog.createNamespace(ACCOUNT_ID, "to-delete", NamespaceType.PROJECT);

        catalog.tombstone(ACCOUNT_ID, "to-delete");

        List<NamespaceRecord> accessible = catalog.listAccessible(ACCOUNT_ID);
        assertThat(accessible).noneMatch(ns -> ns.slug().equals("to-delete"));

        // Default namespace cannot be tombstoned
        assertThatThrownBy(() -> catalog.tombstone(ACCOUNT_ID, "default"))
                .isInstanceOf(DefaultNamespaceProtectedException.class);

        assertThatThrownBy(() -> catalog.tombstone(ACCOUNT_ID, ACCOUNT_ID))
                .isInstanceOf(DefaultNamespaceProtectedException.class);
    }
}
