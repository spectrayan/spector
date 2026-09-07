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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.model.UserSoul;
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.AccountFlags;
import com.spectrayan.spector.synapse.catalog.AccountProfile;
import com.spectrayan.spector.synapse.catalog.AccountQuotas;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.PrincipalKind;
import com.spectrayan.spector.synapse.catalog.exception.TokenNamespaceLockedException;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.identity.IdentityPlane;

@DisplayName("MemoryRequestBinder Specifications")
class MemoryRequestBinderTest {

    private AccountCatalog catalog;
    private MemoryRegistry registry;
    private NamespaceResolver resolver;
    private SynapseProperties synapseProps;
    private IdentityPlane identityPlane;
    private SpectorMemory memory;
    private AutoCloseable lease;
    private MemoryRequestBinder binder;

    @BeforeEach
    void setUp() {
        catalog = mock(AccountCatalog.class);
        registry = mock(MemoryRegistry.class);
        resolver = mock(NamespaceResolver.class);
        when(registry.namespaceResolver()).thenReturn(resolver);

        synapseProps = mock(SynapseProperties.class);
        var authProps = mock(com.spectrayan.spector.config.properties.AuthProperties.class);
        when(authProps.enabled()).thenReturn(true);
        when(synapseProps.auth()).thenReturn(authProps);

        identityPlane = mock(IdentityPlane.class);
        memory = mock(SpectorMemory.class);
        lease = mock(AutoCloseable.class);
        when(memory.acquireLease()).thenReturn(lease);

        ObjectProvider<SpectorMemory> sharedProvider = mock(ObjectProvider.class);
        when(sharedProvider.getIfAvailable()).thenReturn(memory);

        ObjectProvider<IdentityPlane> identityProvider = mock(ObjectProvider.class);
        when(identityProvider.getIfAvailable()).thenReturn(identityPlane);

        binder = new MemoryRequestBinder(catalog, registry, synapseProps, sharedProvider, identityProvider);
    }

    @Test
    @DisplayName("Binds memory with lease and request context, and unbind releases lease")
    void bindAndUnbindWithLease() throws Exception {
        String accountId = "0123456789abc";
        Account account = new Account(accountId, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                "Alice", AccountQuotas.forProfile(AccountProfile.HUMAN_SOLO), AccountFlags.forProfile(AccountProfile.HUMAN_SOLO), "default-ns-id", Instant.now());
        when(catalog.getOrCreateAccount(accountId)).thenReturn(account);
        when(registry.resolveFor(accountId)).thenReturn(memory);
        when(resolver.resolve(accountId, "default-ns-id")).thenReturn(memory);
        // Fail-closed authorization mock — OWNER grant for own default namespace
        when(catalog.authorize(accountId, "default-ns-id", com.spectrayan.spector.synapse.catalog.GrantRole.READER))
                .thenReturn(Optional.of(new com.spectrayan.spector.synapse.catalog.Grant(
                        "grant-" + accountId, com.spectrayan.spector.synapse.catalog.GrantObjectType.NAMESPACE, "default-ns-id",
                        accountId, com.spectrayan.spector.synapse.catalog.PrincipalType.ACCOUNT,
                        com.spectrayan.spector.synapse.catalog.GrantRole.OWNER,
                        null, accountId, Instant.now(), null, null)));

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn(accountId);

        UserSoul soul = new UserSoul(accountId, "Alice", "Researcher", null, null);
        when(identityPlane.primarySoulFor(accountId)).thenReturn(Optional.of(soul));
        when(identityPlane.soulsFor(null, List.of(), accountId)).thenReturn(List.of(soul));

        MemoryBinding binding = binder.bind(auth, Optional.empty());

        assertThat(binding.memory()).isEqualTo(memory);
        assertThat(binding.accountId()).isEqualTo(accountId);
        assertThat(binding.lease()).isEqualTo(lease);
        assertThat(binding.requestMemoryContext()).isNotNull();
        assertThat(binding.requestMemoryContext().primarySoul()).isEqualTo(soul);

        // Verify unbind releases the lease
        binder.unbind(binding);
        verify(lease).close();
    }

    @Test
    @DisplayName("Enforces token allow-set on slugs and rejects unauthorized slug with TokenNamespaceLockedException")
    void tokenNamespaceSlugLock() {
        String accountId = "0123456789abc";
        Account account = new Account(accountId, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                "Alice", AccountQuotas.forProfile(AccountProfile.HUMAN_SOLO), AccountFlags.forProfile(AccountProfile.HUMAN_SOLO), "ns-default", Instant.now());
        when(catalog.getOrCreateAccount(accountId)).thenReturn(account);

        NamespaceRecord projectAlpha = new NamespaceRecord("ns-alpha", "project-alpha", accountId,
                NamespaceType.PROJECT, NamespaceStatus.ACTIVE, "Alpha", "Project Alpha", null, Instant.now(), Instant.now());
        when(catalog.resolve(accountId, "project-alpha")).thenReturn(Optional.of(projectAlpha));
        when(resolver.resolve(accountId, "ns-alpha")).thenReturn(memory);
        when(catalog.authorize(accountId, "ns-alpha", com.spectrayan.spector.synapse.catalog.GrantRole.READER))
                .thenReturn(Optional.of(new com.spectrayan.spector.synapse.catalog.Grant(
                        "grant-alpha", com.spectrayan.spector.synapse.catalog.GrantObjectType.NAMESPACE, "ns-alpha",
                        accountId, com.spectrayan.spector.synapse.catalog.PrincipalType.ACCOUNT,
                        com.spectrayan.spector.synapse.catalog.GrantRole.OWNER,
                        null, accountId, Instant.now(), null, null)));

        NamespaceRecord projectBeta = new NamespaceRecord("ns-beta", "project-beta", accountId,
                NamespaceType.PROJECT, NamespaceStatus.ACTIVE, "Beta", "Project Beta", null, Instant.now(), Instant.now());
        when(catalog.resolve(accountId, "project-beta")).thenReturn(Optional.of(projectBeta));

        // Token locked to [project-alpha]
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .subject(accountId)
                .claim("ns", List.of("project-alpha"))
                .build();
        Authentication auth = new JwtAuthenticationToken(jwt, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));

        // Binding to project-alpha succeeds
        MemoryBinding binding = binder.bind(auth, Optional.of("project-alpha"));
        assertThat(binding.slug()).isEqualTo("project-alpha");

        // Binding to project-beta fails with TokenNamespaceLockedException
        assertThatThrownBy(() -> binder.bind(auth, Optional.of("project-beta")))
                .isInstanceOf(TokenNamespaceLockedException.class);
    }
}
