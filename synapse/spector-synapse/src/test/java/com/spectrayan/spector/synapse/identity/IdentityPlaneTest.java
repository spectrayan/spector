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
package com.spectrayan.spector.synapse.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.SpectorMemoryAdmin;
import com.spectrayan.spector.memory.insula.InsularCortex;
import com.spectrayan.spector.memory.model.InsulaSelfModel;
import com.spectrayan.spector.memory.model.InterestLevel;
import com.spectrayan.spector.memory.model.PersonaContext;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.model.TenantSoul;
import com.spectrayan.spector.memory.model.UserSoul;

import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.GrantAction;

@DisplayName("IdentityPlane Specifications")
class IdentityPlaneTest {

    private IdentityCache identityCache;
    private IdentityPlane identityPlane;
    private ObjectMapper mapper;
    private AccountCatalog catalog;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        identityCache = new IdentityCache(tempDir);
        mapper = JsonMapper.builder().findAndAddModules().build();
        catalog = mock(AccountCatalog.class);
        // Permit all identity reads for tests
        when(catalog.authorizeIdentity(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(GrantAction.class)))
                .thenReturn(true);
        identityPlane = new IdentityPlane(identityCache, mapper, catalog);
    }

    @AfterEach
    void tearDown() {
        identityCache.close();
    }

    @Test
    @DisplayName("Updates and reads account primary soul")
    void readWritePrimarySoul() {
        UserSoul soul = new UserSoul("acc-123", "Alice", "Security Lead", null, null, (short) 1, Instant.now(), Instant.now());
        identityPlane.updateAccountSoul("acc-123", soul);

        Optional<SoulContext> read = identityPlane.primarySoulFor("acc-123");
        assertThat(read).isPresent();
        assertThat(read.get().name()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("Updates and reads account salience profile")
    void readWriteSalienceProfile() {
        SalienceProfile profile = SalienceProfile.builder()
                .interest("threat-intel", InterestLevel.CRITICAL)
                .build();
        identityPlane.updateAccountSalience("acc-123", profile);

        Optional<SalienceProfile> read = identityPlane.salienceFor("acc-123");
        assertThat(read).isPresent();
        assertThat(read.get().interests()).hasSize(1);
    }

    @Test
    @DisplayName("Assembles soul stack across tenant and account")
    void assembleSoulStack() {
        TenantSoul tenantSoul = new TenantSoul("ten-hospital", "Acme Health", "HIPAA Compliant",
                List.of("medical"), List.of("HIPAA"), null, (short) 1, Instant.now(), Instant.now());
        identityCache.getOrOpenTenant("ten-hospital").writeSoul(tenantSoul);

        UserSoul userSoul = new UserSoul("acc-doc", "Dr. Bob", "Cardiologist", null, null, (short) 1, Instant.now(), Instant.now());
        identityPlane.updateAccountSoul("acc-doc", userSoul);

        List<SoulContext> stack = identityPlane.soulsFor("ten-hospital", List.of(), "acc-doc");
        assertThat(stack).hasSize(2);
        assertThat(stack.get(0).name()).isEqualTo("Acme Health");
        assertThat(stack.get(1).name()).isEqualTo("Dr. Bob");
    }

    @Test
    @DisplayName("Region 24 migration copies soul and salience once from default memory")
    void region24Migration() throws Exception {
        String accountId = "acc-legacy-01";

        // Setup legacy default rememberer with InsularCortex
        UserSoul legacySoul = new UserSoul(accountId, "Charlie", "Legacy User", null, null);
        SalienceProfile legacySalience = SalienceProfile.builder().interest("legacy", InterestLevel.HIGH).build();
        InsulaSelfModel selfModel = new InsulaSelfModel("USER", legacySoul, legacySalience, null);
        byte[] insulaJsonBytes = mapper.writeValueAsBytes(selfModel);

        InsularCortex insularCortex = mock(InsularCortex.class);
        when(insularCortex.get()).thenReturn(Optional.of(insulaJsonBytes));

        SpectorMemoryAdmin admin = mock(SpectorMemoryAdmin.class);
        when(admin.insularCortex()).thenReturn(insularCortex);

        SpectorMemory defaultMemory = mock(SpectorMemory.class);
        when(defaultMemory.admin()).thenReturn(admin);

        // Prior to migration, identity bundle is empty
        assertThat(identityPlane.primarySoulFor(accountId)).isEmpty();

        // Perform migration
        identityPlane.checkAndMigrateRegion24(accountId, defaultMemory);

        // Identity bundle now contains the migrated soul and salience
        Optional<SoulContext> migratedSoul = identityPlane.primarySoulFor(accountId);
        assertThat(migratedSoul).isPresent();
        assertThat(migratedSoul.get().name()).isEqualTo("Charlie");

        Optional<SalienceProfile> migratedSalience = identityPlane.salienceFor(accountId);
        assertThat(migratedSalience).isPresent();
        assertThat(migratedSalience.get().interests()).hasSize(1);
    }
}
