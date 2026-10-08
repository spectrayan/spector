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
package com.spectrayan.spector.synapse.connector.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.synapse.connector.model.CredentialCategory;
import com.spectrayan.spector.synapse.connector.model.CredentialRecord;
import com.spectrayan.spector.synapse.connector.model.CredentialType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class JdbcCredentialRepositoryAdversarialTest {

    private JdbcCredentialRepository repository;
    private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:db/migration/V3__multi_user_auth.sql")
                .addScript("classpath:db/migration/V5__credentials.sql")
                .addScript("classpath:db/migration/V10__retire_api_keys_and_add_credential_key_hash.sql")
                .generateUniqueName(true)
                .build();

        jdbcClient = JdbcClient.create(dataSource);
        ObjectMapper mapper = new ObjectMapper();
        repository = new JdbcCredentialRepository(jdbcClient, mapper);
    }

    @Nested
    @DisplayName("findByKeyHash Edge Cases & Adversarial Stress Tests")
    class FindByKeyHashEdgeCases {

        @Test
        @DisplayName("Empty, blank, and null inputs return Optional.empty()")
        void nullAndBlankInputs() {
            assertThat(repository.findByKeyHash(null)).isEmpty();
            assertThat(repository.findByKeyHash("")).isEmpty();
            assertThat(repository.findByKeyHash("   ")).isEmpty();
            assertThat(repository.findByKeyHash("\t\n  \r\n")).isEmpty();
        }

        @Test
        @DisplayName("Non-existent 64-character hash returns Optional.empty()")
        void nonExistentValidHash() {
            String nonExistent = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
            assertThat(repository.findByKeyHash(nonExistent)).isEmpty();
        }

        @Test
        @DisplayName("Strings shorter than 64 characters return Optional.empty()")
        void shortStrings() {
            assertThat(repository.findByKeyHash("a")).isEmpty();
            assertThat(repository.findByKeyHash("1234567890")).isEmpty();
            assertThat(repository.findByKeyHash("11223344556677889900aabbccddeeff")).isEmpty(); // 32 chars
            assertThat(repository.findByKeyHash("11223344556677889900aabbccddeeff11223344556677889900aabbccddeef")).isEmpty(); // 63 chars
        }

        @Test
        @DisplayName("Strings longer than 64 characters (column length) do not crash and return Optional.empty()")
        void stringsLongerThan64Chars() {
            String chars65 = "11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff1";
            assertThatCode(() -> assertThat(repository.findByKeyHash(chars65)).isEmpty()).doesNotThrowAnyException();

            String chars128 = chars65.repeat(2);
            assertThatCode(() -> assertThat(repository.findByKeyHash(chars128)).isEmpty()).doesNotThrowAnyException();

            String chars500 = "x".repeat(500);
            assertThatCode(() -> assertThat(repository.findByKeyHash(chars500)).isEmpty()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("SQL Injection vectors in keyHash are neutralized and return Optional.empty()")
        void sqlInjectionVectors() {
            List<String> vectors = List.of(
                    "' OR '1'='1",
                    "' OR 1=1 --",
                    "'; DROP TABLE credentials; --",
                    "admin'--",
                    "' UNION SELECT null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null --",
                    "112233' OR '1'='1"
            );

            for (String vector : vectors) {
                assertThatCode(() -> assertThat(repository.findByKeyHash(vector)).isEmpty())
                        .as("SQL injection vector: %s", vector)
                        .doesNotThrowAnyException();
            }
        }

        @Test
        @DisplayName("Unicode, null bytes, and non-ASCII characters return Optional.empty() without crashing")
        void unicodeAndSpecialChars() {
            List<String> payloads = List.of(
                    "\u0000",
                    "🔑".repeat(32),
                    "русскийтекст_русскийтекст_русскийтекст_русскийтекст_русскийтекст",
                    "日本語のテスト文字列_日本語のテスト文字列_日本語のテスト文字列_12345"
            );

            for (String payload : payloads) {
                assertThatCode(() -> assertThat(repository.findByKeyHash(payload)).isEmpty())
                        .as("Payload: %s", payload)
                        .doesNotThrowAnyException();
            }
        }

        @Test
        @DisplayName("Hash Case Sensitivity: Saving uppercase hash vs lowercase querying")
        void caseSensitivityMismatch() {
            String upperHash = "AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899";
            String lowerHash = upperHash.toLowerCase();

            CredentialRecord recordWithUpper = CredentialRecord.builder(
                    UUID.randomUUID().toString(), "tenant-case", "cred-upper",
                    CredentialCategory.AUTH, "internal")
                    .credentialType(CredentialType.API_KEY)
                    .ciphertext("enc")
                    .iv("iv")
                    .authTag("tag")
                    .maskedPreview("sk_••••")
                    .keyHash(upperHash)
                    .userId("user-case")
                    .build();

            repository.save(recordWithUpper);

            // Directly inspect what was saved in the database
            String storedInDb = jdbcClient.sql("SELECT key_hash FROM credentials WHERE name = 'cred-upper'")
                    .query(String.class)
                    .single();
            System.out.println("Stored key_hash in DB: [" + storedInDb + "]");

            // Query by upperHash
            Optional<CredentialRecord> foundWithUpper = repository.findByKeyHash(upperHash);
            // Query by lowerHash
            Optional<CredentialRecord> foundWithLower = repository.findByKeyHash(lowerHash);

            System.out.println("foundWithUpper: " + foundWithUpper.isPresent());
            System.out.println("foundWithLower: " + foundWithLower.isPresent());

            // Check whether saving uppercase hash broke lookup because findByKeyHash forces toLowerCase()!
            assertThat(foundWithUpper)
                    .as("Credential saved with uppercase keyHash must be retrievable by findByKeyHash(upperHash)")
                    .isPresent();
            assertThat(foundWithLower)
                    .as("Credential saved with uppercase keyHash must be retrievable by findByKeyHash(lowerHash)")
                    .isPresent();
        }

        @Test
        @DisplayName("Duplicate key_hash handling: Multiple records with identical hash")
        void duplicateKeyHashHandling() {
            String duplicateHash = "3333333333333333333333333333333333333333333333333333333333333333";

            CredentialRecord r1 = CredentialRecord.builder(
                    UUID.randomUUID().toString(), "tenant-dup1", "dup-cred-1",
                    CredentialCategory.AUTH, "internal")
                    .ciphertext("c").iv("i").authTag("t").maskedPreview("m").keyHash(duplicateHash).build();

            CredentialRecord r2 = CredentialRecord.builder(
                    UUID.randomUUID().toString(), "tenant-dup2", "dup-cred-2",
                    CredentialCategory.AUTH, "internal")
                    .ciphertext("c").iv("i").authTag("t").maskedPreview("m").keyHash(duplicateHash).build();

            repository.save(r1);
            repository.save(r2);

            // Because query().optional() encounters 2 records, it catches DataAccessException and fails closed to Optional.empty()
            Optional<CredentialRecord> result = repository.findByKeyHash(duplicateHash);
            System.out.println("Duplicate hash query result: " + result);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Expired credentials with valid keyHash are retrieved by repository (filter responsibility)")
        void expiredCredentialReturnedByRepository() {
            String expiredHash = "4444444444444444444444444444444444444444444444444444444444444444";
            Instant pastTime = Instant.now().minus(1, ChronoUnit.DAYS);

            CredentialRecord expiredRecord = CredentialRecord.builder(
                    UUID.randomUUID().toString(), "tenant-exp", "expired-key",
                    CredentialCategory.AUTH, "internal")
                    .ciphertext("c").iv("i").authTag("t").maskedPreview("m")
                    .keyHash(expiredHash)
                    .expiresAt(pastTime)
                    .build();

            repository.save(expiredRecord);

            Optional<CredentialRecord> found = repository.findByKeyHash(expiredHash);
            assertThat(found).isPresent();
            assertThat(found.get().expiresAt()).isNotNull();
            assertThat(found.get().expiresAt()).isBefore(Instant.now());
        }
    }

    @Nested
    @DisplayName("findAll() and findAllByUserId() Multi-Tenant & Multi-User Isolation Tests")
    class MultiTenantMultiUserTests {

        @Test
        @DisplayName("Multi-tenant and multi-user isolation verification")
        void multiTenantMatrix() {
            // Setup matrix:
            // Tenant Alpha:
            //   - User 1: 3 credentials
            //   - User 2: 2 credentials
            //   - User null (system): 1 credential
            // Tenant Beta:
            //   - User 1: 2 credentials (same user ID in different tenant)
            //   - User 3: 1 credential
            //   - User null (system): 1 credential
            // Tenant Gamma:
            //   - User 2: 1 credential

            createCred("tenant-alpha", "alpha-u1-a", "user-1", "prov-a");
            createCred("tenant-alpha", "alpha-u1-b", "user-1", "prov-b");
            createCred("tenant-alpha", "alpha-u1-c", "user-1", "prov-c");
            createCred("tenant-alpha", "alpha-u2-a", "user-2", "prov-a");
            createCred("tenant-alpha", "alpha-u2-b", "user-2", "prov-b");
            createCred("tenant-alpha", "alpha-sys", null, "prov-sys");

            createCred("tenant-beta", "beta-u1-a", "user-1", "prov-a");
            createCred("tenant-beta", "beta-u1-b", "user-1", "prov-b");
            createCred("tenant-beta", "beta-u3-a", "user-3", "prov-a");
            createCred("tenant-beta", "beta-sys", null, "prov-sys");

            createCred("tenant-gamma", "gamma-u2-a", "user-2", "prov-a");

            // 1. Fleet-wide findAll() must return all 11 credentials
            List<CredentialRecord> all = repository.findAll();
            assertThat(all).hasSize(11);

            // Verify sort order: ORDER BY tenant_id, provider, name
            for (int i = 0; i < all.size() - 1; i++) {
                CredentialRecord current = all.get(i);
                CredentialRecord next = all.get(i + 1);
                int tenantCmp = current.tenantId().compareTo(next.tenantId());
                if (tenantCmp == 0) {
                    int provCmp = current.provider().compareTo(next.provider());
                    if (provCmp == 0) {
                        assertThat(current.name().compareTo(next.name())).isLessThanOrEqualTo(0);
                    } else {
                        assertThat(provCmp).isLessThan(0);
                    }
                } else {
                    assertThat(tenantCmp).isLessThan(0);
                }
            }

            // 2. Fleet-wide findAllByUserId("user-1") must return exactly 5 credentials across Alpha and Beta
            List<CredentialRecord> user1All = repository.findAllByUserId("user-1");
            assertThat(user1All).hasSize(5);
            assertThat(user1All).extracting(CredentialRecord::tenantId)
                    .containsOnly("tenant-alpha", "tenant-beta");
            assertThat(user1All).extracting(CredentialRecord::name)
                    .containsExactlyInAnyOrder("alpha-u1-a", "alpha-u1-b", "alpha-u1-c", "beta-u1-a", "beta-u1-b");

            // 3. Fleet-wide findAllByUserId("user-2") must return exactly 3 credentials across Alpha and Gamma
            List<CredentialRecord> user2All = repository.findAllByUserId("user-2");
            assertThat(user2All).hasSize(3);
            assertThat(user2All).extracting(CredentialRecord::name)
                    .containsExactlyInAnyOrder("alpha-u2-a", "alpha-u2-b", "gamma-u2-a");

            // 4. Fleet-wide findAllByUserId("user-3") must return exactly 1 credential in Beta
            List<CredentialRecord> user3All = repository.findAllByUserId("user-3");
            assertThat(user3All).hasSize(1);
            assertThat(user3All.get(0).name()).isEqualTo("beta-u3-a");

            // 5. Compare with tenant-scoped findByUserId:
            List<CredentialRecord> user1AlphaOnly = repository.findByUserId("tenant-alpha", "user-1");
            assertThat(user1AlphaOnly).hasSize(3);

            List<CredentialRecord> user1BetaOnly = repository.findByUserId("tenant-beta", "user-1");
            assertThat(user1BetaOnly).hasSize(2);

            // 6. User ID edge cases
            assertThat(repository.findAllByUserId(null)).isEmpty();
            assertThat(repository.findAllByUserId("")).isEmpty();
            assertThat(repository.findAllByUserId("   ")).isEmpty();
            assertThat(repository.findAllByUserId("non-existent-user")).isEmpty();
        }

        private void createCred(String tenantId, String name, String userId, String provider) {
            CredentialRecord r = CredentialRecord.builder(
                    UUID.randomUUID().toString(), tenantId, name,
                    CredentialCategory.AUTH, provider)
                    .ciphertext("c").iv("i").authTag("t").maskedPreview("m")
                    .userId(userId)
                    .build();
            repository.save(r);
        }
    }

    @Nested
    @DisplayName("H2 and PostgreSQL DDL Syntax & Migration Verification")
    class DdlSyntaxVerification {

        @Test
        @DisplayName("V10 migration idempotency: running twice on H2 does not throw")
        void v10IdempotencyOnH2() {
            String v10Sql = """
                    ALTER TABLE credentials ADD COLUMN IF NOT EXISTS key_hash VARCHAR(64);
                    CREATE INDEX IF NOT EXISTS idx_credentials_key_hash ON credentials(key_hash);
                    DROP TABLE IF EXISTS api_keys;
                    """;

            // Run second time on existing table
            assertThatCode(() -> {
                for (String stmt : v10Sql.split(";")) {
                    String trimmed = stmt.trim();
                    if (!trimmed.isEmpty()) {
                        jdbcClient.sql(trimmed).update();
                    }
                }
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("V10 migration compatibility in H2 PostgreSQL compatibility mode")
        void v10PostgresqlCompatibilityMode() throws Exception {
            String h2PgUrl = "jdbc:h2:mem:pgtest_" + UUID.randomUUID().toString().replace("-", "")
                    + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";

            try (Connection conn = DriverManager.getConnection(h2PgUrl, "sa", "")) {
                try (Statement stmt = conn.createStatement()) {
                    // Create base schema (V3 users & api_keys, V5 credentials)
                    stmt.execute("""
                            CREATE TABLE users (
                                user_id VARCHAR(13) PRIMARY KEY,
                                username VARCHAR(64) UNIQUE NOT NULL
                            );
                            CREATE TABLE api_keys (
                                key_id VARCHAR(13) PRIMARY KEY,
                                user_id VARCHAR(13) NOT NULL,
                                key_hash VARCHAR(64) NOT NULL,
                                CONSTRAINT fk_api_keys_user FOREIGN KEY (user_id) REFERENCES users (user_id)
                            );
                            CREATE TABLE credentials (
                                credential_id VARCHAR(64) PRIMARY KEY,
                                tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                                user_id VARCHAR(64),
                                name VARCHAR(128) NOT NULL,
                                category VARCHAR(32) NOT NULL,
                                provider VARCHAR(64) NOT NULL,
                                credential_type VARCHAR(32) NOT NULL DEFAULT 'API_KEY',
                                ciphertext CLOB NOT NULL,
                                iv VARCHAR(32) NOT NULL,
                                auth_tag VARCHAR(32) NOT NULL,
                                masked_preview VARCHAR(128) NOT NULL,
                                properties_json CLOB,
                                is_default BOOLEAN NOT NULL DEFAULT FALSE,
                                description VARCHAR(255),
                                version INT NOT NULL DEFAULT 1,
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                expires_at TIMESTAMP,
                                last_used_at TIMESTAMP,
                                CONSTRAINT uq_credentials_tenant_name UNIQUE (tenant_id, name)
                            );
                            """);

                    // Execute V10 statements in PostgreSQL mode
                    stmt.execute("ALTER TABLE credentials ADD COLUMN IF NOT EXISTS key_hash VARCHAR(64)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_credentials_key_hash ON credentials(key_hash)");
                    stmt.execute("DROP TABLE IF EXISTS api_keys");

                    // Verify api_keys is dropped
                    try {
                        stmt.executeQuery("SELECT * FROM api_keys");
                        org.junit.jupiter.api.Assertions.fail("api_keys should have been dropped");
                    } catch (Exception expected) {
                        System.out.println("Verified api_keys is dropped: " + expected.getMessage());
                    }

                    // Verify key_hash column exists on credentials
                    stmt.execute("INSERT INTO credentials (credential_id, tenant_id, name, category, provider, ciphertext, iv, auth_tag, masked_preview, key_hash) "
                            + "VALUES ('c1', 't1', 'n1', 'AUTH', 'p1', 'c', 'i', 't', 'm', '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef')");

                    var rs = stmt.executeQuery("SELECT key_hash FROM credentials WHERE credential_id = 'c1'");
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("key_hash")).isEqualTo("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

                    // Execute V10 again to verify idempotency in PostgreSQL mode
                    stmt.execute("ALTER TABLE credentials ADD COLUMN IF NOT EXISTS key_hash VARCHAR(64)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_credentials_key_hash ON credentials(key_hash)");
                    stmt.execute("DROP TABLE IF EXISTS api_keys");
                }
            }
        }
    }
}
