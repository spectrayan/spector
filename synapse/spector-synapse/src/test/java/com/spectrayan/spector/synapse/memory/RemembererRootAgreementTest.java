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
package com.spectrayan.spector.synapse.memory;

import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.migration.TenantNamespaceStartupDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Every component that touches a rememberer path must agree on the root (Req R3.1).
 *
 * <p>This test exists because they did not. The resolver used
 * {@code spector.memory.persistence-path} while the migrator and startup detector used
 * {@code spector.data-dir}, and the CLI defaulted to {@code ~/.spector/data}. Since the shipped
 * {@code application.yml} sets {@code persistence-path} to {@code ${SPECTOR_DATA_DIR}/cognitive},
 * the migrator scanned a tree that never contained any namespaces: every candidate fell through the
 * "source does not exist" branch, was counted as {@code skipped}, and the run reported success
 * having moved nothing. The detector, on the same wrong root, reported zero unmigrated namespaces
 * and let readiness pass.</p>
 *
 * <p>The failure mode was entirely silent, so the guard is an equality assertion across components
 * rather than a behavioural test.</p>
 */
@DisplayName("Req R3.1: All rememberer-path components agree on one root")
class RemembererRootAgreementTest {

    @TempDir
    Path tempDir;

    private SynapseProperties propsWithSplitRoots() {
        // Mirrors the shipped application.yml: data-dir and persistence-path are different trees,
        // with the rememberer root nested one level below the identity root.
        SynapseProperties props = new SynapseProperties();
        props.setDataDir(tempDir.toString());
        props.getMemory().setPersistencePath(tempDir.resolve("cognitive").toString());
        return props;
    }

    @Test
    @DisplayName("remembererRoot() honours persistence-path and is distinct from identityRoot()")
    void remembererRootHonoursPersistencePathAndIsDistinctFromIdentityRoot() {
        SynapseProperties props = propsWithSplitRoots();

        assertThat(props.remembererRoot())
                .as("rememberer root must come from spector.memory.persistence-path")
                .isEqualTo(tempDir.resolve("cognitive"));

        assertThat(props.identityRoot())
                .as("identity root must come from spector.data-dir")
                .isEqualTo(tempDir);

        assertThat(props.remembererRoot())
                .as("the two roots are different planes and must not collapse into one")
                .isNotEqualTo(props.identityRoot());
    }

    @Test
    @DisplayName("A blank persistence-path is ignored by MemoryProperties, so the framework default stands")
    void blankPersistencePathIsIgnoredAndFrameworkDefaultStands() {
        SynapseProperties props = new SynapseProperties();
        props.setDataDir(tempDir.toString());
        props.getMemory().setPersistencePath("   ");

        // MemoryProperties.setPersistencePath silently ignores null/blank values, so persistence-path
        // is never actually empty at runtime and remembererRoot()'s data-dir fallback is defensive
        // rather than reachable through configuration. Asserted here so the fallback is not mistaken
        // for live behaviour by a future reader.
        assertThat(props.remembererRoot())
                .as("a blank persistence-path must leave the framework default in place, not fall through to data-dir")
                .isEqualTo(java.nio.file.Path.of(".spector", "memory"));
    }

    @Test
    @DisplayName("remembererRoot() falls back to data-dir when memory properties carry no path at all")
    void remembererRootFallsBackToDataDirWhenNoPathPresent() {
        SynapseProperties props = new SynapseProperties() {
            @Override
            public com.spectrayan.spector.config.properties.MemoryProperties getMemory() {
                return null;
            }
        };
        props.setDataDir(tempDir.toString());

        assertThat(props.remembererRoot()).isEqualTo(tempDir);
    }

    @Test
    @DisplayName("NamespaceResolver, migrator, and startup detector all resolve the same root")
    void resolverMigratorAndDetectorAgree() throws Exception {
        SynapseProperties props = propsWithSplitRoots();
        Path expected = props.remembererRoot();

        AccountCatalog catalog = mock(AccountCatalog.class);
        when(catalog.listTenantedAccounts()).thenReturn(List.of());

        // Resolver
        try (NamespaceResolver resolver = new NamespaceResolver(
                catalog, props, null, null, null, null, null, null, null, null, null, 10)) {
            Method basePath = NamespaceResolver.class.getDeclaredMethod("basePath");
            basePath.setAccessible(true);
            assertThat((Path) basePath.invoke(resolver))
                    .as("NamespaceResolver must open under the rememberer root")
                    .isEqualTo(expected);
        }

        // Startup detector
        TenantNamespaceStartupDetector detector = new TenantNamespaceStartupDetector(props, catalog);
        Field detectorBase = TenantNamespaceStartupDetector.class.getDeclaredField("basePath");
        detectorBase.setAccessible(true);
        assertThat((Path) detectorBase.get(detector))
                .as("TenantNamespaceStartupDetector must inspect the rememberer root, not data-dir")
                .isEqualTo(expected);

        // Migrator
        var migrator = new com.spectrayan.spector.synapse.migration.TenantNamespaceMigrator(
                props, catalog, null, null);
        Field migratorBase = com.spectrayan.spector.synapse.migration.TenantNamespaceMigrator.class
                .getDeclaredField("basePath");
        migratorBase.setAccessible(true);
        assertThat((Path) migratorBase.get(migrator))
                .as("TenantNamespaceMigrator must move files under the rememberer root, not data-dir")
                .isEqualTo(expected);
    }
}
