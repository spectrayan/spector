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

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.memory.model.TenantSoul;
import com.spectrayan.spector.memory.model.UserSoul;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.GrantAction;

@DisplayName("IdentityPlane Multi-Soul Hierarchy Specification (ADR-0029 §2.5)")
class IdentityPlaneMultiSoulTest {

    @TempDir
    Path tempDir;

    private IdentityPlane identityPlane;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        IdentityCache identityCache = new IdentityCache(tempDir);
        AccountCatalog catalog = mock(AccountCatalog.class);
        when(catalog.authorizeIdentity(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(GrantAction.class)))
                .thenReturn(true);
        identityPlane = new IdentityPlane(identityCache, mapper, catalog);
    }

    @Test
    @DisplayName("Assembles multi-soul hierarchy [TenantSoul, UserSoul]")
    void assemblesMultiSoulHierarchy() {
        String tenantId = "ten-healthcare-1";
        String accountId = "acc-auditor-1";

        TenantSoul tenantSoul = new TenantSoul(
                tenantId,
                "Health System",
                "Regional healthcare provider",
                List.of("hipaa", "audit"),
                List.of("access review"),
                new float[]{0.1f, 0.2f},
                (short) 1,
                Instant.now(),
                Instant.now()
        );
        identityPlane.updateTenantSoul(tenantId, tenantSoul);

        UserSoul userSoul = new UserSoul(
                accountId,
                "Dr. Smith",
                "Senior Compliance Auditor",
                null,
                new float[]{0.5f, 0.5f},
                (short) 2,
                Instant.now(),
                Instant.now()
        );
        identityPlane.updateAccountSoul(accountId, userSoul);

        List<SoulContext> stack = identityPlane.soulsFor(tenantId, List.of(), accountId);

        assertThat(stack).hasSize(2);
        assertThat(stack.get(0)).isInstanceOf(TenantSoul.class);
        assertThat(stack.get(1)).isInstanceOf(UserSoul.class);
        assertThat(((TenantSoul) stack.get(0)).id()).isEqualTo(tenantId);
        assertThat(((UserSoul) stack.get(1)).id()).isEqualTo(accountId);
    }
}
