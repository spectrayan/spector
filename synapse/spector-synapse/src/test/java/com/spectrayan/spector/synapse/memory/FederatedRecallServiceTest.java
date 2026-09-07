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

import com.spectrayan.spector.commons.error.SpectorValidationException;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.memory.cortex.MemorySource;
import com.spectrayan.spector.memory.model.CognitiveResult;
import com.spectrayan.spector.memory.model.MemoryType;
import com.spectrayan.spector.memory.model.RecallOptions;
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
import com.spectrayan.spector.synapse.catalog.exception.FederationDisabledException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FederatedRecallService — Unit Tests")
class FederatedRecallServiceTest {

    @Mock
    private AccountCatalog catalog;

    @Mock
    private MemoryRegistry memoryRegistry;

    @Mock
    private NamespaceResolver namespaceResolver;

    private FederatedRecallService federatedService;

    private static final String ACCOUNT_ID = "01JXYZTEST001";
    private static final String NS_1 = "01JXYZNS00001";
    private static final String NS_2 = "01JXYZNS00002";
    private static final String NS_3 = "01JXYZNS00003";

    @BeforeEach
    void setUp() {
        federatedService = new FederatedRecallService(catalog, memoryRegistry);
    }

    @Test
    @DisplayName("Throws FederationDisabledException when account federation flag is disabled")
    void testFederationDisabled() {
        Account account = new Account(
                ACCOUNT_ID,
                PrincipalKind.HUMAN,
                AccountProfile.HUMAN_SOLO,
                "Test User",
                new AccountQuotas(4, 2, 10_000_000L, 10_000L),
                new AccountFlags(true, false, false), // federation = false
                ACCOUNT_ID,
                Instant.now()
        );
        when(catalog.getOrCreateAccount(ACCOUNT_ID)).thenReturn(account);

        FederatedRecallRequest request = new FederatedRecallRequest(
                "query text",
                List.of("default"),
                10,
                5,
                3000,
                2,
                null,
                null
        );

        assertThatThrownBy(() -> federatedService.federatedRecall(ACCOUNT_ID, request))
                .isInstanceOf(FederationDisabledException.class)
                .hasMessageContaining(ACCOUNT_ID);
    }

    @Test
    @DisplayName("Throws SpectorValidationException on empty query")
    void testEmptyQueryValidation() {
        FederatedRecallRequest request = new FederatedRecallRequest(
                "",
                List.of("default"),
                10,
                5,
                3000,
                2,
                null,
                null
        );

        assertThatThrownBy(() -> federatedService.federatedRecall(ACCOUNT_ID, request))
                .isInstanceOf(SpectorValidationException.class);
    }

    @Test
    @DisplayName("Successful fan-out across multiple namespaces with provenance annotation and heuristic merge")
    void testSuccessfulFederatedRecall() {
        Account account = new Account(
                ACCOUNT_ID,
                PrincipalKind.SERVICE,
                AccountProfile.SERVICE,
                "Service Account",
                new AccountQuotas(64, 8, 100_000_000L, 100_000L),
                new AccountFlags(true, true, true), // federation = true
                NS_1,
                Instant.now()
        );
        when(catalog.getOrCreateAccount(ACCOUNT_ID)).thenReturn(account);

        NamespaceRecord rec1 = new NamespaceRecord(NS_1, "default", ACCOUNT_ID, NamespaceType.DEFAULT, NamespaceStatus.ACTIVE, "Default", "", null, Instant.now(), null, false);
        NamespaceRecord rec2 = new NamespaceRecord(NS_2, "project-alpha", ACCOUNT_ID, NamespaceType.PROJECT, NamespaceStatus.ACTIVE, "Alpha", "", null, Instant.now(), null, false);

        when(catalog.resolve(ACCOUNT_ID, "default")).thenReturn(Optional.of(rec1));
        when(catalog.resolve(ACCOUNT_ID, "project-alpha")).thenReturn(Optional.of(rec2));
        when(catalog.authorize(ACCOUNT_ID, NS_1, GrantRole.READER)).thenReturn(Optional.of(new Grant("g1", GrantObjectType.NAMESPACE, NS_1, ACCOUNT_ID, PrincipalType.ACCOUNT, GrantRole.OWNER, null, ACCOUNT_ID, Instant.now(), null, null)));
        when(catalog.authorize(ACCOUNT_ID, NS_2, GrantRole.READER)).thenReturn(Optional.of(new Grant("g2", GrantObjectType.NAMESPACE, NS_2, ACCOUNT_ID, PrincipalType.ACCOUNT, GrantRole.WRITER, null, ACCOUNT_ID, Instant.now(), null, null)));

        when(memoryRegistry.namespaceResolver()).thenReturn(namespaceResolver);
        when(namespaceResolver.isHot(NS_1)).thenReturn(true);
        when(namespaceResolver.isHot(NS_2)).thenReturn(true);

        SpectorMemory mem1 = mock(SpectorMemory.class);
        SpectorMemory mem2 = mock(SpectorMemory.class);
        when(namespaceResolver.resolve(ACCOUNT_ID, NS_1)).thenReturn(mem1);
        when(namespaceResolver.resolve(ACCOUNT_ID, NS_2)).thenReturn(mem2);

        CognitiveResult r1 = new CognitiveResult("mem-1", "Database timeout error", 0.95f, 0.8f, 0.1f, (short) 1, (byte) 0, MemoryType.EPISODIC, MemorySource.OBSERVED, new String[]{"db"}, 1.0f, 1.0f);
        CognitiveResult r2 = new CognitiveResult("mem-2", "Java concurrency best practices", 0.85f, 0.7f, 0.2f, (short) 2, (byte) 0, MemoryType.SEMANTIC, MemorySource.OBSERVED, new String[]{"java"}, 1.0f, 1.0f);
        when(mem1.recall(eq("concurrency database"), any(RecallOptions.class))).thenReturn(List.of(r1));
        when(mem2.recall(eq("concurrency database"), any(RecallOptions.class))).thenReturn(List.of(r2));

        FederatedRecallRequest request = new FederatedRecallRequest(
                "concurrency database",
                List.of("default", "project-alpha"),
                5,
                5,
                3000,
                2,
                null,
                null
        );

        FederatedRecallResponse response = federatedService.federatedRecall(ACCOUNT_ID, request);

        assertThat(response).isNotNull();
        assertThat(response.hits()).hasSize(2);

        FederatedRecallHit hit1 = response.hits().get(0);
        assertThat(hit1.id()).isEqualTo("mem-1");
        assertThat(hit1.namespaceId()).isEqualTo(NS_1);
        assertThat(hit1.slug()).isEqualTo("default");
        assertThat(hit1.role()).isEqualTo(GrantRole.OWNER);
        assertThat(hit1.localRank()).isEqualTo(1);
        assertThat(hit1.heuristicGlobalRank()).isEqualTo(1);

        FederatedRecallHit hit2 = response.hits().get(1);
        assertThat(hit2.id()).isEqualTo("mem-2");
        assertThat(hit2.namespaceId()).isEqualTo(NS_2);
        assertThat(hit2.slug()).isEqualTo("project-alpha");
        assertThat(hit2.role()).isEqualTo(GrantRole.WRITER);
        assertThat(hit2.localRank()).isEqualTo(1);
        assertThat(hit2.heuristicGlobalRank()).isEqualTo(2);

        assertThat(response.summary().openedNamespaces()).containsExactlyInAnyOrder("default", "project-alpha");
        assertThat(response.summary().skippedColdNamespaces()).isEmpty();
        assertThat(response.summary().deniedNamespaces()).isEmpty();
        assertThat(response.summary().failedNamespaces()).isEmpty();
    }

    @Test
    @DisplayName("Respects maxColdOpens budget and marks excess cold namespaces as skippedCold")
    void testColdOpenBudgetEnforcement() {
        Account account = new Account(
                ACCOUNT_ID,
                PrincipalKind.SERVICE,
                AccountProfile.SERVICE,
                "Service Account",
                new AccountQuotas(64, 8, 100_000_000L, 100_000L),
                new AccountFlags(true, true, true),
                NS_1,
                Instant.now()
        );
        when(catalog.getOrCreateAccount(ACCOUNT_ID)).thenReturn(account);

        NamespaceRecord rec1 = new NamespaceRecord(NS_1, "ns-1", ACCOUNT_ID, NamespaceType.PROJECT, NamespaceStatus.ACTIVE, "NS1", "", null, Instant.now(), null, false);
        NamespaceRecord rec2 = new NamespaceRecord(NS_2, "ns-2", ACCOUNT_ID, NamespaceType.PROJECT, NamespaceStatus.ACTIVE, "NS2", "", null, Instant.now(), null, false);

        when(catalog.resolve(ACCOUNT_ID, "ns-1")).thenReturn(Optional.of(rec1));
        when(catalog.resolve(ACCOUNT_ID, "ns-2")).thenReturn(Optional.of(rec2));
        when(catalog.authorize(ACCOUNT_ID, NS_1, GrantRole.READER)).thenReturn(Optional.of(new Grant("g1", GrantObjectType.NAMESPACE, NS_1, ACCOUNT_ID, PrincipalType.ACCOUNT, GrantRole.OWNER, null, ACCOUNT_ID, Instant.now(), null, null)));
        when(catalog.authorize(ACCOUNT_ID, NS_2, GrantRole.READER)).thenReturn(Optional.of(new Grant("g2", GrantObjectType.NAMESPACE, NS_2, ACCOUNT_ID, PrincipalType.ACCOUNT, GrantRole.OWNER, null, ACCOUNT_ID, Instant.now(), null, null)));

        when(memoryRegistry.namespaceResolver()).thenReturn(namespaceResolver);
        when(namespaceResolver.isHot(NS_1)).thenReturn(false); // cold
        when(namespaceResolver.isHot(NS_2)).thenReturn(false); // cold

        SpectorMemory mem1 = mock(SpectorMemory.class);
        when(namespaceResolver.resolve(ACCOUNT_ID, NS_1)).thenReturn(mem1);
        when(mem1.recall(eq("test"), any(RecallOptions.class))).thenReturn(List.of());

        // maxColdOpens = 1 -> ns-1 opened, ns-2 skipped
        FederatedRecallRequest request = new FederatedRecallRequest(
                "test",
                List.of("ns-1", "ns-2"),
                10,
                5,
                3000,
                1, // maxColdOpens = 1
                null,
                null
        );

        FederatedRecallResponse response = federatedService.federatedRecall(ACCOUNT_ID, request);

        assertThat(response.summary().openedNamespaces()).containsExactly("ns-1");
        assertThat(response.summary().skippedColdNamespaces()).containsExactly("ns-2");
    }

    @Test
    @DisplayName("Querying 'granted' expands to all accessible namespaces")
    void testQueryAllGrantedExpansion() {
        Account account = new Account(
                ACCOUNT_ID,
                PrincipalKind.SERVICE,
                AccountProfile.SERVICE,
                "Service Account",
                new AccountQuotas(64, 8, 100_000_000L, 100_000L),
                new AccountFlags(true, true, true),
                NS_1,
                Instant.now()
        );
        when(catalog.getOrCreateAccount(ACCOUNT_ID)).thenReturn(account);

        NamespaceRecord rec1 = new NamespaceRecord(NS_1, "default", ACCOUNT_ID, NamespaceType.DEFAULT, NamespaceStatus.ACTIVE, "Default", "", null, Instant.now(), null, false);
        NamespaceRecord rec2 = new NamespaceRecord(NS_2, "team-kb", ACCOUNT_ID, NamespaceType.SHARED, NamespaceStatus.ACTIVE, "KB", "", null, Instant.now(), null, false);

        when(catalog.listAccessible(ACCOUNT_ID)).thenReturn(List.of(rec1, rec2));
        when(catalog.authorize(ACCOUNT_ID, NS_1, GrantRole.READER)).thenReturn(Optional.of(new Grant("g1", GrantObjectType.NAMESPACE, NS_1, ACCOUNT_ID, PrincipalType.ACCOUNT, GrantRole.OWNER, null, ACCOUNT_ID, Instant.now(), null, null)));
        when(catalog.authorize(ACCOUNT_ID, NS_2, GrantRole.READER)).thenReturn(Optional.of(new Grant("g2", GrantObjectType.NAMESPACE, NS_2, ACCOUNT_ID, PrincipalType.ACCOUNT, GrantRole.READER, null, ACCOUNT_ID, Instant.now(), null, null)));

        when(memoryRegistry.namespaceResolver()).thenReturn(namespaceResolver);
        when(namespaceResolver.isHot(NS_1)).thenReturn(true);
        when(namespaceResolver.isHot(NS_2)).thenReturn(true);
        SpectorMemory mem1 = mock(SpectorMemory.class);
        SpectorMemory mem2 = mock(SpectorMemory.class);
        when(namespaceResolver.resolve(ACCOUNT_ID, NS_1)).thenReturn(mem1);
        when(namespaceResolver.resolve(ACCOUNT_ID, NS_2)).thenReturn(mem2);

        when(mem1.recall(eq("test query"), any(RecallOptions.class))).thenReturn(List.of());
        when(mem2.recall(eq("test query"), any(RecallOptions.class))).thenReturn(List.of());

        FederatedRecallRequest request = new FederatedRecallRequest(
                "test query",
                List.of("granted"),
                10,
                5,
                3000,
                2,
                null,
                null
        );

        FederatedRecallResponse response = federatedService.federatedRecall(ACCOUNT_ID, request);

        assertThat(response.summary().openedNamespaces()).containsExactlyInAnyOrder("default", "team-kb");
    }
}
