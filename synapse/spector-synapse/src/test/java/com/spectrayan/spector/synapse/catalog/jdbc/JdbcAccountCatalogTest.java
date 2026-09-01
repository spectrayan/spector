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
package com.spectrayan.spector.synapse.catalog.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.spectrayan.spector.synapse.catalog.*;
import com.spectrayan.spector.synapse.catalog.exception.AccountQuotaExceededException;
import com.spectrayan.spector.synapse.catalog.exception.DefaultNamespaceProtectedException;
import com.spectrayan.spector.synapse.catalog.exception.NamespaceNotFoundException;
import com.spectrayan.spector.synapse.config.sql.SqlQueryLoader;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JdbcAccountCatalog Tests (Phase 3)")
class JdbcAccountCatalogTest {

    private JdbcClient jdbc;
    private JdbcAccountCatalog catalog;
    private ObjectMapper objectMapper;

    private static final String ACCOUNT_ID = "0195500000001";
    private static final String OTHER_ACCOUNT_ID = "0195500000002";

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:jdbccatalog-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");

        // Run Flyway migrations V1..V6
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        jdbc = JdbcClient.create(dataSource);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SqlQueryLoader sqlLoader = new SqlQueryLoader();
        sqlLoader.prewarm();

        catalog = new JdbcAccountCatalog(jdbc, sqlLoader, objectMapper);
    }

    @Test
    @DisplayName("getOrCreateAccount initializes default namespace and implicit OWNER grant")
    void testGetOrCreateAccount() {
        Account account = catalog.getOrCreateAccount(ACCOUNT_ID);
        assertThat(account.id()).isEqualTo(ACCOUNT_ID);
        assertThat(account.defaultNamespaceId()).isEqualTo(ACCOUNT_ID);
        assertThat(account.profile()).isEqualTo(AccountProfile.HUMAN_SOLO);
        assertThat(account.quotas().maxNamespaces()).isEqualTo(4);
        assertThat(account.quotas().maxHotNamespaces()).isEqualTo(2);

        // Verify default namespace resolved
        Optional<NamespaceRecord> defaultNs = catalog.resolve(ACCOUNT_ID, "default");
        assertThat(defaultNs).isPresent();
        assertThat(defaultNs.get().slug()).isEqualTo("default");
        assertThat(defaultNs.get().namespaceId()).isEqualTo(ACCOUNT_ID);
        assertThat(defaultNs.get().type()).isEqualTo(NamespaceType.DEFAULT);
        assertThat(defaultNs.get().status()).isEqualTo(NamespaceStatus.ACTIVE);

        // Verify implicit OWNER authorization
        Optional<Grant> auth = catalog.authorize(ACCOUNT_ID, ACCOUNT_ID, GrantRole.OWNER);
        assertThat(auth).isPresent();
        assertThat(auth.get().role()).isEqualTo(GrantRole.OWNER);
    }

    @Test
    @DisplayName("createNamespace creates and persists rich metadata with domain bias")
    void testCreateNamespaceWithMetadata() {
        catalog.getOrCreateAccount(ACCOUNT_ID);

        var bias = new NamespaceBias(List.of("distributed-systems", "database"), Map.of("consensus", 1.5f));
        NamespaceRecord created = catalog.createNamespace(
                ACCOUNT_ID, "raft-consensus", NamespaceType.PROJECT,
                "Raft Consensus", "Raft protocol research", bias
        );

        assertThat(created.slug()).isEqualTo("raft-consensus");
        assertThat(created.displayName()).isEqualTo("Raft Consensus");
        assertThat(created.description()).isEqualTo("Raft protocol research");
        assertThat(created.bias()).isNotNull();
        assertThat(created.bias().domainFocus()).contains("distributed-systems", "database");

        // Verify resolved by slug
        Optional<NamespaceRecord> bySlug = catalog.resolve(ACCOUNT_ID, "raft-consensus");
        assertThat(bySlug).isPresent();
        assertThat(bySlug.get().namespaceId()).isEqualTo(created.namespaceId());

        // Verify resolved by TSID
        Optional<NamespaceRecord> byId = catalog.resolve(ACCOUNT_ID, created.namespaceId());
        assertThat(byId).isPresent();
        assertThat(byId.get().slug()).isEqualTo("raft-consensus");
    }

    @Test
    @DisplayName("createNamespace enforces slug grammar and duplicate prevention")
    void testSlugGrammarAndDuplicatePrevention() {
        catalog.getOrCreateAccount(ACCOUNT_ID);

        assertThatThrownBy(() -> catalog.createNamespace(ACCOUNT_ID, "-invalid-start", NamespaceType.PROJECT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> catalog.createNamespace(ACCOUNT_ID, "invalid space", NamespaceType.PROJECT))
                .isInstanceOf(IllegalArgumentException.class);

        catalog.createNamespace(ACCOUNT_ID, "project-1", NamespaceType.PROJECT);

        assertThatThrownBy(() -> catalog.createNamespace(ACCOUNT_ID, "project-1", NamespaceType.PROJECT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("createNamespace enforces account maxNamespaces quota")
    void testAccountQuotaExceeded() {
        catalog.getOrCreateAccount(ACCOUNT_ID); // creates 1 (default)

        catalog.createNamespace(ACCOUNT_ID, "project-1", NamespaceType.PROJECT); // 2
        catalog.createNamespace(ACCOUNT_ID, "project-2", NamespaceType.PROJECT); // 3
        catalog.createNamespace(ACCOUNT_ID, "project-3", NamespaceType.PROJECT); // 4 (cap reached for HUMAN_SOLO)

        assertThatThrownBy(() -> catalog.createNamespace(ACCOUNT_ID, "project-4", NamespaceType.PROJECT))
                .isInstanceOf(AccountQuotaExceededException.class);
    }

    @Test
    @DisplayName("listAccessible lists owned and granted namespaces")
    void testListAccessible() {
        catalog.getOrCreateAccount(ACCOUNT_ID);
        catalog.getOrCreateAccount(OTHER_ACCOUNT_ID);

        NamespaceRecord owned = catalog.createNamespace(ACCOUNT_ID, "my-project", NamespaceType.PROJECT);
        NamespaceRecord otherOwned = catalog.createNamespace(OTHER_ACCOUNT_ID, "shared-project", NamespaceType.PROJECT);

        // Before grant: ACCOUNT_ID sees default + my-project (2)
        List<NamespaceRecord> list1 = catalog.listAccessible(ACCOUNT_ID);
        assertThat(list1).extracting(NamespaceRecord::slug).containsExactlyInAnyOrder("default", "my-project");

        // Grant ACCOUNT_ID READER on shared-project
        catalog.addGrant(new Grant(
                "grant-001",
                GrantObjectType.NAMESPACE,
                otherOwned.namespaceId(),
                ACCOUNT_ID,
                PrincipalType.ACCOUNT,
                GrantRole.READER,
                Set.of(GrantAction.READ),
                OTHER_ACCOUNT_ID,
                Instant.now(),
                null,
                null
        ));

        // After grant: ACCOUNT_ID sees default + my-project + shared-project (3)
        List<NamespaceRecord> list2 = catalog.listAccessible(ACCOUNT_ID);
        assertThat(list2).extracting(NamespaceRecord::slug).containsExactlyInAnyOrder("default", "my-project", "shared-project");
    }

    @Test
    @DisplayName("setDefaultNamespace and tombstone invariants")
    void testSetDefaultAndTombstone() {
        catalog.getOrCreateAccount(ACCOUNT_ID);
        NamespaceRecord proj = catalog.createNamespace(ACCOUNT_ID, "work-ctx", NamespaceType.PROJECT);

        catalog.setDefaultNamespace(ACCOUNT_ID, proj.namespaceId());
        Account acct = catalog.getAccount(ACCOUNT_ID);
        assertThat(acct.defaultNamespaceId()).isEqualTo(proj.namespaceId());

        // Default namespace deletion protected
        assertThatThrownBy(() -> catalog.tombstone(ACCOUNT_ID, ACCOUNT_ID))
                .isInstanceOf(DefaultNamespaceProtectedException.class);
        assertThatThrownBy(() -> catalog.tombstone(ACCOUNT_ID, proj.namespaceId()))
                .isInstanceOf(DefaultNamespaceProtectedException.class);

        // Create another project and delete it
        NamespaceRecord disposable = catalog.createNamespace(ACCOUNT_ID, "temp-project", NamespaceType.PROJECT);
        catalog.tombstone(ACCOUNT_ID, disposable.namespaceId());

        Optional<NamespaceRecord> resolved = catalog.resolve(ACCOUNT_ID, "temp-project");
        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("updateNamespace modifies metadata")
    void testUpdateNamespace() {
        catalog.getOrCreateAccount(ACCOUNT_ID);
        NamespaceRecord created = catalog.createNamespace(ACCOUNT_ID, "dev", NamespaceType.PROJECT, "Dev", "Old desc", null);

        var newBias = new NamespaceBias(List.of("java"), Map.of("backend", 1.2f));
        NamespaceRecord updated = catalog.updateNamespace(
                ACCOUNT_ID, "dev", "Development", "New desc", NamespaceType.PROJECT, newBias
        );

        assertThat(updated.displayName()).isEqualTo("Development");
        assertThat(updated.description()).isEqualTo("New desc");
        assertThat(updated.bias()).isNotNull();
        assertThat(updated.bias().domainFocus()).contains("java");
    }

    @Test
    @DisplayName("grant lifecycle: add, authorize, revoke")
    void testGrantLifecycle() {
        catalog.getOrCreateAccount(ACCOUNT_ID);
        catalog.getOrCreateAccount(OTHER_ACCOUNT_ID);

        NamespaceRecord proj = catalog.createNamespace(ACCOUNT_ID, "kb", NamespaceType.PROJECT);

        catalog.addGrant(new Grant(
                "grant-kb-1",
                GrantObjectType.NAMESPACE,
                proj.namespaceId(),
                OTHER_ACCOUNT_ID,
                PrincipalType.ACCOUNT,
                GrantRole.WRITER,
                Set.of(GrantAction.READ, GrantAction.WRITE),
                ACCOUNT_ID,
                Instant.now(),
                null,
                null
        ));

        Optional<Grant> authWriter = catalog.authorize(OTHER_ACCOUNT_ID, proj.namespaceId(), GrantRole.WRITER);
        assertThat(authWriter).isPresent();

        Optional<Grant> authAdmin = catalog.authorize(OTHER_ACCOUNT_ID, proj.namespaceId(), GrantRole.ADMIN);
        assertThat(authAdmin).isEmpty(); // WRITER does not satisfy ADMIN

        catalog.revokeGrant("grant-kb-1");

        Optional<Grant> authAfterRevoke = catalog.authorize(OTHER_ACCOUNT_ID, proj.namespaceId(), GrantRole.READER);
        assertThat(authAfterRevoke).isEmpty();
    }

    @Test
    @DisplayName("grantNamespace, listGrants, and revokeNamespaceGrant in JDBC catalog")
    void testGrantNamespaceAndListAndRevoke() {
        catalog.getOrCreateAccount(ACCOUNT_ID);
        catalog.getOrCreateAccount(OTHER_ACCOUNT_ID);

        NamespaceRecord proj = catalog.createNamespace(ACCOUNT_ID, "shared-docs", NamespaceType.PROJECT);

        Grant grant = catalog.grantNamespace(ACCOUNT_ID, "shared-docs", OTHER_ACCOUNT_ID, GrantRole.READER, null, null);
        assertThat(grant).isNotNull();
        assertThat(grant.role()).isEqualTo(GrantRole.READER);

        List<Grant> grants = catalog.listGrants(ACCOUNT_ID, "shared-docs");
        assertThat(grants).hasSize(2); // Implicit owner + READER grant
        assertThat(grants).anyMatch(g -> g.grantId().equals(grant.grantId()));

        catalog.revokeNamespaceGrant(ACCOUNT_ID, "shared-docs", grant.grantId());

        List<Grant> grantsAfterRevoke = catalog.listGrants(ACCOUNT_ID, "shared-docs");
        assertThat(grantsAfterRevoke).hasSize(1); // Only implicit owner remains
        assertThat(grantsAfterRevoke).noneMatch(g -> g.grantId().equals(grant.grantId()));
    }

    @Test
    @DisplayName("authorizeIdentity enforces region PEP, constraints, and ADMIN !=> INJECT")
    void testAuthorizeIdentityRules() {
        catalog.getOrCreateAccount(ACCOUNT_ID);
        catalog.getOrCreateAccount(OTHER_ACCOUNT_ID);

        // 1. Owner always authorized on own bundle
        assertThat(catalog.authorizeIdentity(ACCOUNT_ID, ACCOUNT_ID, "SOUL", GrantAction.READ)).isTrue();
        assertThat(catalog.authorizeIdentity(ACCOUNT_ID, ACCOUNT_ID, "SOUL", GrantAction.WRITE)).isTrue();
        assertThat(catalog.authorizeIdentity(ACCOUNT_ID, ACCOUNT_ID, "SOUL", GrantAction.INJECT)).isTrue();

        // 2. Cross-account unauthorized before grant
        assertThat(catalog.authorizeIdentity(OTHER_ACCOUNT_ID, ACCOUNT_ID, "SOUL", GrantAction.READ)).isFalse();

        // 3. Grant direct region grant for SOUL READ
        catalog.addGrant(new Grant(
                "0195500000004",
                GrantObjectType.IDENTITY_REGION,
                ACCOUNT_ID + ":SOUL",
                OTHER_ACCOUNT_ID,
                PrincipalType.ACCOUNT,
                GrantRole.READER,
                Set.of(GrantAction.READ),
                ACCOUNT_ID,
                Instant.now(),
                null,
                null
        ));
        assertThat(catalog.authorizeIdentity(OTHER_ACCOUNT_ID, ACCOUNT_ID, "SOUL", GrantAction.READ)).isTrue();
        assertThat(catalog.authorizeIdentity(OTHER_ACCOUNT_ID, ACCOUNT_ID, "SOUL", GrantAction.WRITE)).isFalse();
        assertThat(catalog.authorizeIdentity(OTHER_ACCOUNT_ID, ACCOUNT_ID, "SOUL", GrantAction.INJECT)).isFalse();

        // 4. Grant ADMIN on bundle with region constraints: only SALIENCE
        catalog.addGrant(new Grant(
                "0195500000005",
                GrantObjectType.IDENTITY_BUNDLE,
                ACCOUNT_ID,
                OTHER_ACCOUNT_ID,
                PrincipalType.ACCOUNT,
                GrantRole.ADMIN,
                Set.of(GrantAction.READ, GrantAction.WRITE, GrantAction.ADMIN),
                ACCOUNT_ID,
                Instant.now(),
                null,
                new GrantConstraints(null, null, null, Set.of("SALIENCE"))
        ));

        // SALIENCE allows READ, WRITE, ADMIN
        assertThat(catalog.authorizeIdentity(OTHER_ACCOUNT_ID, ACCOUNT_ID, "SALIENCE", GrantAction.READ)).isTrue();
        assertThat(catalog.authorizeIdentity(OTHER_ACCOUNT_ID, ACCOUNT_ID, "SALIENCE", GrantAction.WRITE)).isTrue();
        // ADMIN does NOT imply INJECT
        assertThat(catalog.authorizeIdentity(OTHER_ACCOUNT_ID, ACCOUNT_ID, "SALIENCE", GrantAction.INJECT)).isFalse();
        // POLICY is not in region constraints
        assertThat(catalog.authorizeIdentity(OTHER_ACCOUNT_ID, ACCOUNT_ID, "POLICY", GrantAction.READ)).isFalse();

        // 5. Grant explicit INJECT on bundle
        catalog.addGrant(new Grant(
                "0195500000006",
                GrantObjectType.IDENTITY_BUNDLE,
                ACCOUNT_ID,
                OTHER_ACCOUNT_ID,
                PrincipalType.ACCOUNT,
                GrantRole.READER,
                Set.of(GrantAction.INJECT),
                ACCOUNT_ID,
                Instant.now(),
                null,
                null
        ));
        assertThat(catalog.authorizeIdentity(OTHER_ACCOUNT_ID, ACCOUNT_ID, "SOUL", GrantAction.INJECT)).isTrue();
    }

    @Test
    @DisplayName("addOrgMember validates org unit existence and throws when missing")
    void testAddOrgMemberValidation() {
        catalog.getOrCreateAccount(ACCOUNT_ID);

        // Non-existent org unit should throw IllegalArgumentException
        assertThatThrownBy(() -> catalog.addOrgMember(ACCOUNT_ID, "non-existent-org"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist in catalog");

        // Insert legitimate org unit into DB
        jdbc.sql("INSERT INTO org_units (org_unit_id, tenant_id, name) VALUES (:id, :tid, :name)")
                .param("id", "engineering")
                .param("tid", "tenant-1")
                .param("name", "Engineering")
                .update();

        // Now addOrgMember succeeds
        catalog.addOrgMember(ACCOUNT_ID, "engineering");
        List<String> orgs = catalog.orgUnitIdsForAccount(ACCOUNT_ID);
        assertThat(orgs).contains("engineering");

        // Remove org member
        catalog.removeOrgMember(ACCOUNT_ID, "engineering");
        assertThat(catalog.orgUnitIdsForAccount(ACCOUNT_ID)).doesNotContain("engineering");
    }
}

