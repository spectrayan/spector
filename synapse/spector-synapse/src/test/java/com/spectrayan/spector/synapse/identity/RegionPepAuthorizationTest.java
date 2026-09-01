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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.memory.identity.IdentityBundle;
import com.spectrayan.spector.memory.identity.IdentityRegionId;
import com.spectrayan.spector.memory.model.SalienceProfile;
import com.spectrayan.spector.memory.model.SoulContext;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.GrantAction;

@DisplayName("Region PEP and IdentityPlane Authorization Specifications")
class RegionPepAuthorizationTest {

    @Test
    @DisplayName("soulsFor requires INJECT on tenant SOUL and ORG_DIR regions")
    void testSoulsForRequiresInject() {
        IdentityCache cache = mock(IdentityCache.class);
        ObjectMapper mapper = new ObjectMapper();
        AccountCatalog catalog = mock(AccountCatalog.class);

        IdentityPlane plane = new IdentityPlane(cache, mapper, catalog);

        SoulContext tenantSoul = new com.spectrayan.spector.memory.model.TenantSoul(
                "tenant-1", "tenant-root", "desc", List.of(), List.of(), null, (short) 0, null, null);
        SoulContext orgSoul = new com.spectrayan.spector.memory.model.OrgUnitSoul(
                "org-eng", "engineering", "desc", List.of(), null, (short) 0, null, null);
        SoulContext userSoul = new com.spectrayan.spector.memory.model.UserSoul(
                "user-1", "user-solo", "desc", null, null);

        IdentityBundle tenantBundle = mock(IdentityBundle.class);
        when(tenantBundle.readSoul()).thenReturn(Optional.of(tenantSoul));
        when(tenantBundle.readOrgUnitSoul("org-eng")).thenReturn(Optional.of(orgSoul));
        when(cache.openTenant("tenant-1")).thenAnswer(inv -> mockHandle(tenantBundle));

        IdentityBundle userBundle = mock(IdentityBundle.class);
        when(userBundle.readSoul()).thenReturn(Optional.of(userSoul));
        when(cache.openAccount("user-1")).thenAnswer(inv -> mockHandle(userBundle));

        // Permitted on SOUL and ORG_DIR with INJECT
        when(catalog.authorizeIdentity("user-1", "tenant-1", IdentityRegionId.SOUL.name(), GrantAction.INJECT))
                .thenReturn(true);
        when(catalog.authorizeIdentity("user-1", "tenant-1", IdentityRegionId.ORG_DIR.name(), GrantAction.INJECT))
                .thenReturn(true);
        when(catalog.authorizeIdentity("user-1", "user-1", IdentityRegionId.SOUL.name(), GrantAction.READ))
                .thenReturn(true);

        List<SoulContext> souls = plane.soulsFor("tenant-1", List.of("org-eng"), "user-1");

        assertThat(souls).containsExactly(tenantSoul, orgSoul, userSoul);
        verify(catalog).authorizeIdentity("user-1", "tenant-1", IdentityRegionId.SOUL.name(), GrantAction.INJECT);
        verify(catalog).authorizeIdentity("user-1", "tenant-1", IdentityRegionId.ORG_DIR.name(), GrantAction.INJECT);
    }

    @Test
    @DisplayName("soulsFor excludes tenant souls when INJECT grant is denied")
    void testSoulsForDeniedInject() {
        IdentityCache cache = mock(IdentityCache.class);
        ObjectMapper mapper = new ObjectMapper();
        AccountCatalog catalog = mock(AccountCatalog.class);

        IdentityPlane plane = new IdentityPlane(cache, mapper, catalog);

        SoulContext userSoul = new com.spectrayan.spector.memory.model.UserSoul(
                "user-1", "user-solo", "desc", null, null);
        IdentityBundle userBundle = mock(IdentityBundle.class);
        when(userBundle.readSoul()).thenReturn(Optional.of(userSoul));
        when(cache.openAccount("user-1")).thenAnswer(inv -> mockHandle(userBundle));

        // Deny INJECT on tenant SOUL
        when(catalog.authorizeIdentity("user-1", "tenant-1", IdentityRegionId.SOUL.name(), GrantAction.INJECT))
                .thenReturn(false);
        when(catalog.authorizeIdentity("user-1", "user-1", IdentityRegionId.SOUL.name(), GrantAction.READ))
                .thenReturn(true);

        List<SoulContext> souls = plane.soulsFor("tenant-1", List.of("org-eng"), "user-1");

        assertThat(souls).containsExactly(userSoul);
    }

    @Test
    @DisplayName("primarySoulFor and salienceFor require READ permission")
    void testReadRequiresReadAction() {
        IdentityCache cache = mock(IdentityCache.class);
        ObjectMapper mapper = new ObjectMapper();
        AccountCatalog catalog = mock(AccountCatalog.class);

        IdentityPlane plane = new IdentityPlane(cache, mapper, catalog);

        SoulContext userSoul = new com.spectrayan.spector.memory.model.UserSoul(
                "user-1", "user-solo", "desc", null, null);
        IdentityBundle userBundle = mock(IdentityBundle.class);
        when(userBundle.readSoul()).thenReturn(Optional.of(userSoul));
        when(userBundle.readSalience()).thenReturn(Optional.of(SalienceProfile.NEUTRAL));
        when(cache.openAccount("user-1")).thenAnswer(inv -> mockHandle(userBundle));

        when(catalog.authorizeIdentity("user-1", "user-1", IdentityRegionId.SOUL.name(), GrantAction.READ))
                .thenReturn(true);
        when(catalog.authorizeIdentity("user-1", "user-1", IdentityRegionId.SALIENCE.name(), GrantAction.READ))
                .thenReturn(true);

        assertThat(plane.primarySoulFor("user-1")).contains(userSoul);
        assertThat(plane.salienceFor("user-1")).contains(SalienceProfile.NEUTRAL);

        verify(catalog).authorizeIdentity("user-1", "user-1", IdentityRegionId.SOUL.name(), GrantAction.READ);
        verify(catalog).authorizeIdentity("user-1", "user-1", IdentityRegionId.SALIENCE.name(), GrantAction.READ);
    }

    private static IdentityCache.IdentityHandle mockHandle(IdentityBundle bundle) {
        return new IdentityCache.IdentityHandle(new IdentityCache.PinnedBundle(bundle));
    }
}
