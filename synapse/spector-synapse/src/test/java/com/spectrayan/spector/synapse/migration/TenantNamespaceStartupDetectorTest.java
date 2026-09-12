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
package com.spectrayan.spector.synapse.migration;

import com.spectrayan.spector.kernel.storage.NamespacePathResolver;
import com.spectrayan.spector.kernel.storage.StoragePaths;
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.AccountFlags;
import com.spectrayan.spector.synapse.catalog.AccountProfile;
import com.spectrayan.spector.synapse.catalog.AccountQuotas;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.PrincipalKind;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Startup readiness detector behaviour (Task 4.5, Req R5.8, and the C4 ordering fix).
 */
@DisplayName("TenantNamespaceStartupDetector: readiness gate and boot-time side effects")
class TenantNamespaceStartupDetectorTest {

    @TempDir
    Path tempDir;

    private static final String TENANT_ACME = "018f9b8c000070008000000000000001";
    private static final String ALICE_ID = "018f9b8c000070008000000000000002";
    private static final String NS_ALICE = "018f9b8c000070008000000000000010";

    private Path remembererRoot;
    private AccountCatalog catalog;
    private SynapseProperties props;

    @BeforeEach
    void setUp() {
        remembererRoot = tempDir.resolve("cognitive");
        props = new SynapseProperties();
        props.setDataDir(tempDir.toString());
        props.getMemory().setPersistencePath(remembererRoot.toString());

        Account alice = new Account(ALICE_ID, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                "Alice", AccountQuotas.forProfile(AccountProfile.HUMAN_SOLO),
                AccountFlags.forProfile(AccountProfile.HUMAN_SOLO),
                NS_ALICE, Instant.now(), TENANT_ACME, false);

        NamespaceRecord record = new NamespaceRecord(
                NS_ALICE, "default", ALICE_ID, NamespaceType.DEFAULT, NamespaceStatus.ACTIVE,
                "Alice default", null, null, Instant.now(), Instant.now());

        catalog = mock(AccountCatalog.class);
        when(catalog.listTenantedAccounts()).thenReturn(List.of(alice));
        // The detector reads owned namespaces, not the accessible view, so tombstoned records are
        // visible to migration (Req R9.1). Mockito does not run interface default methods, so this
        // must be stubbed explicitly rather than inherited from listAccessible.
        when(catalog.listOwnedNamespaces(ALICE_ID)).thenReturn(List.of(record));
        when(catalog.listAccessible(ALICE_ID)).thenReturn(List.of(record));
    }

    private void seedUnmigratedNamespace() throws IOException {
        Path layoutA = NamespacePathResolver.resolve(remembererRoot, null, NS_ALICE).dir();
        Files.createDirectories(layoutA);
        Files.writeString(layoutA.resolve(StoragePaths.FILE_NAMESPACE), "{}");
    }

    @Test
    @DisplayName("Req R5.8: An unmigrated tenanted namespace fails readiness when the layout is enabled")
    void unmigratedNamespaceFailsReadiness() throws IOException {
        seedUnmigratedNamespace();
        props.getNamespace().getTenantRooted().setEnabled(true);

        TenantNamespaceStartupDetector detector = new TenantNamespaceStartupDetector(props, catalog);

        assertThat(detector.countUnmigratedNamespaces())
                .as("the detector must inspect the rememberer root, where the namespace actually is")
                .isEqualTo(1);

        assertThatThrownBy(detector::onApplicationReady)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("migrate-namespaces");
    }

    @Test
    @DisplayName("Req R5.8: A migrated namespace passes readiness")
    void migratedNamespacePassesReadiness() throws IOException {
        Path layoutB = NamespacePathResolver.resolve(remembererRoot, TENANT_ACME, NS_ALICE).dir();
        Files.createDirectories(layoutB);
        Files.writeString(layoutB.resolve(StoragePaths.FILE_NAMESPACE), "{}");
        props.getNamespace().getTenantRooted().setEnabled(true);

        TenantNamespaceStartupDetector detector = new TenantNamespaceStartupDetector(props, catalog);

        assertThat(detector.countUnmigratedNamespaces()).isZero();
        detector.onApplicationReady();
    }

    @Test
    @DisplayName("C4: With the layout disabled, boot performs no filesystem work at all")
    void disabledLayout_performsNoFilesystemWorkOnBoot() throws IOException {
        seedUnmigratedNamespace();
        props.getNamespace().getTenantRooted().setEnabled(false);

        // A leftover staging directory from some earlier attempt.
        Path staging = remembererRoot.resolve(TenantNamespaceMigrator.STAGING_PREFIX + NS_ALICE);
        Files.createDirectories(staging);
        Files.writeString(staging.resolve("leftover.tmp"), "partial");

        new TenantNamespaceStartupDetector(props, catalog).onApplicationReady();

        // The detector used to run staging cleanup before consulting the flag, so a disabled install
        // still deleted directories under the rememberer root on every boot.
        assertThat(staging)
                .as("a disabled feature must not delete anything during startup")
                .exists();
        assertThat(NamespacePathResolver.resolve(remembererRoot, null, NS_ALICE).dir()).exists();
    }
}
