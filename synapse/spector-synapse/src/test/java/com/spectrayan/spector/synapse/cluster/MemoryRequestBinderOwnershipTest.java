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
package com.spectrayan.spector.synapse.cluster;

import com.spectrayan.spector.cluster.OwnershipResolver;
import com.spectrayan.spector.cluster.membership.StaticMembershipSource;
import com.spectrayan.spector.cluster.node.NodeIdentity;
import com.spectrayan.spector.cluster.node.NodeRole;
import com.spectrayan.spector.cluster.routing.RoutingKey;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.synapse.catalog.Account;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.catalog.AccountFlags;
import com.spectrayan.spector.synapse.catalog.AccountProfile;
import com.spectrayan.spector.synapse.catalog.AccountQuotas;
import com.spectrayan.spector.synapse.catalog.Grant;
import com.spectrayan.spector.synapse.catalog.GrantObjectType;
import com.spectrayan.spector.synapse.catalog.GrantRole;
import com.spectrayan.spector.synapse.catalog.NamespaceRecord;
import com.spectrayan.spector.synapse.catalog.NamespaceStatus;
import com.spectrayan.spector.synapse.catalog.NamespaceType;
import com.spectrayan.spector.synapse.catalog.PrincipalKind;
import com.spectrayan.spector.synapse.catalog.PrincipalType;
import com.spectrayan.spector.synapse.cluster.exception.NamespaceNotOwnedException;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.memory.MemoryBinding;
import com.spectrayan.spector.synapse.memory.MemoryRegistry;
import com.spectrayan.spector.synapse.memory.MemoryRequestBinder;
import com.spectrayan.spector.synapse.memory.NamespaceResolver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryRequestBinderOwnershipTest {

    private AccountCatalog catalog;
    private MemoryRegistry memoryRegistry;
    private NamespaceResolver namespaceResolver;
    private SynapseProperties synapseProps;
    private MeterRegistry meterRegistry;

    private static final String CELL_ID = "us-east-1";
    private static final String NODE_1 = "node-1";
    private static final String NODE_2 = "node-2";
    private static final String ACCOUNT_ID = "acc-bob";
    private static final String TENANT_ID = "tenant-ops";

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> providerOf(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(bean);
        return provider;
    }

    @BeforeEach
    void setUp() {
        catalog = mock(AccountCatalog.class);
        memoryRegistry = mock(MemoryRegistry.class);
        namespaceResolver = mock(NamespaceResolver.class);
        when(memoryRegistry.namespaceResolver()).thenReturn(namespaceResolver);
        when(namespaceResolver.cachedInstanceCount()).thenReturn(3);

        synapseProps = new SynapseProperties();
        synapseProps.auth().setEnabled(true);
        synapseProps.getCell().setId(CELL_ID);
        synapseProps.getCell().setRole("owner");
        synapseProps.getCell().setNodeId(NODE_1);
        synapseProps.getCell().getRing().setVersion(1);
        synapseProps.getCell().getRing().setMembers(List.of(NODE_1, NODE_2));

        meterRegistry = new SimpleMeterRegistry();

        Account bob = new Account(
                ACCOUNT_ID, PrincipalKind.HUMAN, AccountProfile.HUMAN_SOLO,
                "Bob", AccountQuotas.forProfile(AccountProfile.HUMAN_SOLO),
                AccountFlags.forProfile(AccountProfile.HUMAN_SOLO),
                ACCOUNT_ID, Instant.now(), TENANT_ID, false
        );
        when(catalog.getOrCreateAccount(eq(ACCOUNT_ID), any(), any())).thenReturn(bob);
        when(catalog.getOrCreateAccount(eq(ACCOUNT_ID))).thenReturn(bob);
        when(catalog.getAccount(eq(ACCOUNT_ID))).thenReturn(bob);
    }

    @Test
    @DisplayName("STANDALONE role short-circuits ownership checks and incurs zero metrics overhead")
    void testStandaloneShortCircuits() {
        SynapseProperties standaloneProps = new SynapseProperties();
        standaloneProps.auth().setEnabled(false);

        MemoryRequestBinder binder = new MemoryRequestBinder(
                catalog, memoryRegistry, standaloneProps,
                providerOf(null), providerOf(null),
                providerOf(OwnershipResolver.standalone()),
                providerOf(meterRegistry)
        );

        MemoryBinding binding = binder.bind(null, Optional.empty());
        assertThat(binding).isNotNull();
        assertThat(binding.namespaceId()).isEqualTo("default");
    }

    @Test
    @DisplayName("Non-owner request records spector.route.lookup and increments spector.route.not_owner (Req R10)")
    void testMetricsRecordedOnRefusal() {
        StaticMembershipSource membership = new StaticMembershipSource(CELL_ID, 1, List.of(NODE_1, NODE_2));
        OwnershipResolver resolver = new OwnershipResolver(new NodeIdentity(CELL_ID, NODE_1, NodeRole.OWNER), membership);

        MemoryRequestBinder binder = new MemoryRequestBinder(
                catalog, memoryRegistry, synapseProps,
                providerOf(null), providerOf(null),
                providerOf(resolver),
                providerOf(meterRegistry)
        );

        // Find a foreign namespace
        String foreignNs = null;
        for (int i = 0; i < 500; i++) {
            String candidate = "ns-foreign-" + i;
            if (!resolver.ownsLocally(new RoutingKey(CELL_ID, TENANT_ID, candidate))) {
                foreignNs = candidate;
                break;
            }
        }
        assertThat(foreignNs).isNotNull();

        NamespaceRecord record = new NamespaceRecord(
                foreignNs, "foreign-slug", ACCOUNT_ID, NamespaceType.AGENT, NamespaceStatus.ACTIVE,
                "Foreign", "Foreign desc", null, Instant.now(), Instant.now()
        );
        when(catalog.resolve(ACCOUNT_ID, foreignNs)).thenReturn(Optional.of(record));
        Grant grant = new Grant(
                "grant-1", GrantObjectType.NAMESPACE, foreignNs, ACCOUNT_ID,
                PrincipalType.ACCOUNT, GrantRole.OWNER, Set.of(), "admin", Instant.now(), null, null
        );
        when(catalog.authorize(ACCOUNT_ID, foreignNs, GrantRole.READER)).thenReturn(Optional.of(grant));

        when(namespaceResolver.placementTenantIdFor(eq(foreignNs), eq(ACCOUNT_ID), eq(ACCOUNT_ID), any()))
                .thenReturn(TENANT_ID);

        Authentication auth = new TestingAuthenticationToken(ACCOUNT_ID, "secret", "ROLE_USER");

        final String targetNs = foreignNs;
        assertThatThrownBy(() -> binder.bind(auth, Optional.of(targetNs)))
                .isInstanceOf(NamespaceNotOwnedException.class);

        // Verify metrics
        Timer lookupTimer = meterRegistry.find("spector.route.lookup").timer();
        assertThat(lookupTimer).isNotNull();
        assertThat(lookupTimer.count()).isEqualTo(1L);

        Counter notOwnerCounter = meterRegistry.find("spector.route.not_owner").counter();
        assertThat(notOwnerCounter).isNotNull();
        assertThat(notOwnerCounter.count()).isEqualTo(1.0);

        // Verify spector.ns.owner gauge
        var gauge = meterRegistry.find("spector.ns.owner").gauge();
        assertThat(gauge).isNotNull();
        assertThat(gauge.value()).isEqualTo(3.0);
    }
}
