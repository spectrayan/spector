/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.spectrayan.spector.synapse.security;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.h2.jdbcx.JdbcDataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.spectrayan.spector.commons.error.ErrorCode;
import com.spectrayan.spector.synapse.catalog.exception.CrossTenantAccessException;
import com.spectrayan.spector.synapse.security.ApiKeyStore.ApiKeyCreation;
import com.spectrayan.spector.synapse.security.ApiKeyStore.ApiKeyRow;

/**
 * Unit test suite for {@link ApiKeyStore} verifying persistence, metadata projection,
 * last-used tracking throttling, and authorization boundaries (Issue #1050).
 */
@DisplayName("ApiKeyStore — Unit Tests")
class ApiKeyStoreTest {

    private static final AtomicLong DB_COUNTER = new AtomicLong();

    private JdbcClient jdbc;
    private ApiKeyStore store;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        String name = "apikeystore-test-" + System.nanoTime() + "-" + DB_COUNTER.incrementAndGet();
        dataSource.setURL("jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1");
        jdbc = JdbcClient.create(dataSource);

        jdbc.sql("""
                CREATE TABLE api_keys (
                    key_id      VARCHAR(13)   NOT NULL,
                    user_id     VARCHAR(13)   NOT NULL,
                    key_hash    VARCHAR(64)   NOT NULL,
                    scopes      VARCHAR(1024) NOT NULL DEFAULT '',
                    expires_at  TIMESTAMP,
                    revoked     BOOLEAN       NOT NULL DEFAULT FALSE,
                    created_at  TIMESTAMP     NOT NULL,
                    name        VARCHAR(255),
                    key_prefix  VARCHAR(32),
                    last_used_at TIMESTAMP,
                    PRIMARY KEY (key_id)
                )
                """).update();

        store = new ApiKeyStore(jdbc);
    }

    @Test
    @DisplayName("create stores name, key prefix and generates non-empty TSID and raw key")
    void createStoresMetadata() {
        ApiKeyCreation creation = store.create("USER_001", "Production Ingestion Key",
                Set.of("memory:read", "memory:write"), Instant.now().plusSeconds(3600));

        assertThat(creation.keyId()).isNotBlank().hasSize(13);
        assertThat(creation.rawKey()).isNotBlank().startsWith("spk_".substring(0, 0)); // base64url

        Optional<ApiKeyRow> rowOpt = store.findById(creation.keyId());
        assertThat(rowOpt).isPresent();
        ApiKeyRow row = rowOpt.get();
        assertThat(row.name()).isEqualTo("Production Ingestion Key");
        assertThat(row.keyPrefix()).startsWith("spk_");
        assertThat(row.userId()).isEqualTo("USER_001");
        assertThat(row.scopes()).containsExactlyInAnyOrder("memory:read", "memory:write");
        assertThat(row.revoked()).isFalse();
        assertThat(row.lastUsedAt()).isNull();
    }

    @Test
    @DisplayName("recordLastUsed updates timestamp and throttles excessive writes")
    void recordLastUsedUpdatesAndThrottles() {
        ApiKeyCreation creation = store.create("USER_001", "Worker Key", Set.of(), null);
        String keyId = creation.keyId();

        store.recordLastUsed(keyId);
        Optional<ApiKeyRow> first = store.findById(keyId);
        assertThat(first).isPresent();
        Instant firstTime = first.get().lastUsedAt();
        assertThat(firstTime).isNotNull();

        // Calling recordLastUsed immediately again should be throttled (no error, remains unchanged)
        store.recordLastUsed(keyId);
        Optional<ApiKeyRow> second = store.findById(keyId);
        assertThat(second.get().lastUsedAt()).isEqualTo(firstTime);
    }

    @Test
    @DisplayName("revokeWithAuthorization: owner can revoke own key")
    void revokeWithAuthorizationOwnerCanRevoke() {
        ApiKeyCreation creation = store.create("OWNER_001", "Personal Key", Set.of(), null);
        boolean revoked = store.revokeWithAuthorization(creation.keyId(), "OWNER_001", "tenant-a", "tenant-a", false, false);

        assertThat(revoked).isTrue();
        Optional<ApiKeyRow> row = store.findById(creation.keyId());
        assertThat(row.get().revoked()).isTrue();
    }

    @Test
    @DisplayName("revokeWithAuthorization: super-admin can revoke any key fleet-wide")
    void revokeWithAuthorizationSuperAdminCanRevoke() {
        ApiKeyCreation creation = store.create("TARGET_USER", "Target Key", Set.of(), null);
        boolean revoked = store.revokeWithAuthorization(creation.keyId(), "SUPER_OP", "platform", "tenant-b", true, false);

        assertThat(revoked).isTrue();
        assertThat(store.findById(creation.keyId()).get().revoked()).isTrue();
    }

    @Test
    @DisplayName("revokeWithAuthorization: tenant admin can revoke key in same tenant")
    void revokeWithAuthorizationTenantAdminSameTenant() {
        ApiKeyCreation creation = store.create("MEMBER_001", "Member Key", Set.of(), null);
        boolean revoked = store.revokeWithAuthorization(creation.keyId(), "ADMIN_001", "tenant-alpha", "tenant-alpha", false, true);

        assertThat(revoked).isTrue();
        assertThat(store.findById(creation.keyId()).get().revoked()).isTrue();
    }

    @Test
    @DisplayName("revokeWithAuthorization: tenant admin cannot revoke key in different tenant (403 SPE-820-001)")
    void revokeWithAuthorizationTenantAdminOtherTenantThrows() {
        ApiKeyCreation creation = store.create("MEMBER_BETA", "Beta Key", Set.of(), null);

        assertThatThrownBy(() -> store.revokeWithAuthorization(
                creation.keyId(), "ADMIN_ALPHA", "tenant-alpha", "tenant-beta", false, true))
                .isInstanceOf(CrossTenantAccessException.class)
                .satisfies(ex -> {
                    CrossTenantAccessException ctae = (CrossTenantAccessException) ex;
                    assertThat(ctae.errorCode()).isEqualTo(ErrorCode.CROSS_TENANT_ACCESS_DENIED);
                    assertThat(ctae.getMessage()).contains("SPE-820-001");
                });
    }

    @Test
    @DisplayName("revokeWithAuthorization: regular user cannot revoke other user's key in same tenant (IDOR attack)")
    void revokeWithAuthorizationRegularUserOtherUserThrows() {
        ApiKeyCreation creation = store.create("VICTIM_001", "Victim Key", Set.of(), null);

        assertThatThrownBy(() -> store.revokeWithAuthorization(
                creation.keyId(), "ATTACKER_001", "tenant-shared", "tenant-shared", false, false))
                .isInstanceOf(CrossTenantAccessException.class)
                .satisfies(ex -> {
                    CrossTenantAccessException ctae = (CrossTenantAccessException) ex;
                    assertThat(ctae.errorCode()).isEqualTo(ErrorCode.CROSS_TENANT_ACCESS_DENIED);
                });
    }

    @Test
    @DisplayName("revokeWithAuthorization: returns false for nonexistent key")
    void revokeWithAuthorizationMissingKeyReturnsFalse() {
        boolean revoked = store.revokeWithAuthorization("NONEXISTENT01", "CALLER", "t1", "t1", false, false);
        assertThat(revoked).isFalse();
    }

    @Test
    @DisplayName("findByUserId lists keys ordered by creation time descending")
    void findByUserIdOrdersDescending() {
        ApiKeyCreation k1 = store.create("USER_MULTI", "Key 1", Set.of(), null);
        ApiKeyCreation k2 = store.create("USER_MULTI", "Key 2", Set.of(), null);

        List<ApiKeyRow> keys = store.findByUserId("USER_MULTI");
        assertThat(keys).hasSize(2);
        assertThat(keys.getFirst().keyId()).isEqualTo(k2.keyId());
        assertThat(keys.get(1).keyId()).isEqualTo(k1.keyId());
    }
}
