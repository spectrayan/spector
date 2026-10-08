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
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcCredentialRepositoryTest {

    private JdbcCredentialRepository repository;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:db/migration/V5__credentials.sql")
                .addScript("classpath:db/migration/V10__retire_api_keys_and_add_credential_key_hash.sql")
                .generateUniqueName(true)
                .build();

        JdbcClient jdbc = JdbcClient.create(dataSource);
        ObjectMapper mapper = new ObjectMapper();
        repository = new JdbcCredentialRepository(jdbc, mapper);
    }

    @Test
    @DisplayName("Save and find credential record with keyHash in repository")
    void saveAndFindRecord() {
        String keyHash = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        CredentialRecord record = CredentialRecord.builder(
                UUID.randomUUID().toString(), "tenant-1", "slack-key",
                CredentialCategory.CHANNEL, "slack")
                .ciphertext("encrypted-payload")
                .iv("iv-bytes")
                .authTag("tag-bytes")
                .maskedPreview("xoxb-••••••••1234")
                .keyHash(keyHash)
                .properties(Map.of("channel", "general"))
                .isDefault(true)
                .build();

        CredentialRecord saved = repository.save(record);
        assertThat(saved.name()).isEqualTo("slack-key");

        Optional<CredentialRecord> found = repository.findByName("tenant-1", "slack-key");
        assertThat(found).isPresent();
        assertThat(found.get().isDefault()).isTrue();
        assertThat(found.get().keyHash()).isEqualTo(keyHash);
        assertThat(found.get().properties()).containsEntry("channel", "general");
    }

    @Test
    @DisplayName("Find credential by keyHash")
    void findByKeyHash() {
        String keyHash = "11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff";
        CredentialRecord authRecord = CredentialRecord.builder(
                UUID.randomUUID().toString(), "tenant-auth", "api-token-1",
                CredentialCategory.AUTH, "internal")
                .credentialType(CredentialType.API_KEY)
                .ciphertext("enc-secret")
                .iv("iv-1")
                .authTag("tag-1")
                .maskedPreview("sk_spec_••••1234")
                .keyHash(keyHash)
                .userId("user-auth-1")
                .build();

        repository.save(authRecord);

        Optional<CredentialRecord> found = repository.findByKeyHash(keyHash);
        assertThat(found).isPresent();
        assertThat(found.get().name()).isEqualTo("api-token-1");
        assertThat(found.get().category()).isEqualTo(CredentialCategory.AUTH);
        assertThat(found.get().keyHash()).isEqualTo(keyHash);
        assertThat(found.get().userId()).isEqualTo("user-auth-1");

        // Case insensitivity test
        Optional<CredentialRecord> foundUpper = repository.findByKeyHash(keyHash.toUpperCase());
        assertThat(foundUpper).isPresent();
        assertThat(foundUpper.get().name()).isEqualTo("api-token-1");

        // Non-existent key hash
        assertThat(repository.findByKeyHash("deadbeefdeadbeef")).isEmpty();
        assertThat(repository.findByKeyHash(null)).isEmpty();
        assertThat(repository.findByKeyHash("   ")).isEmpty();
    }

    @Test
    @DisplayName("Find all credentials fleet-wide across tenants")
    void findAllFleetWide() {
        CredentialRecord t1 = CredentialRecord.builder(UUID.randomUUID().toString(), "tenant-a", "cred-a",
                CredentialCategory.STORAGE, "s3").ciphertext("c").iv("i").authTag("t").maskedPreview("m").build();
        CredentialRecord t2 = CredentialRecord.builder(UUID.randomUUID().toString(), "tenant-b", "cred-b",
                CredentialCategory.DATABASE, "postgres").ciphertext("c").iv("i").authTag("t").maskedPreview("m").build();

        repository.save(t1);
        repository.save(t2);

        List<CredentialRecord> all = repository.findAll();
        assertThat(all).hasSizeGreaterThanOrEqualTo(2);
        assertThat(all).extracting(CredentialRecord::name).contains("cred-a", "cred-b");
    }

    @Test
    @DisplayName("Find all credentials by userId fleet-wide across tenants")
    void findAllByUserIdFleetWide() {
        String targetUser = "user-fleet-99";
        CredentialRecord r1 = CredentialRecord.builder(UUID.randomUUID().toString(), "tenant-x", "cred-x",
                CredentialCategory.APP, "github").userId(targetUser).ciphertext("c").iv("i").authTag("t").maskedPreview("m").build();
        CredentialRecord r2 = CredentialRecord.builder(UUID.randomUUID().toString(), "tenant-y", "cred-y",
                CredentialCategory.VAULT, "hashicorp").userId(targetUser).ciphertext("c").iv("i").authTag("t").maskedPreview("m").build();
        CredentialRecord r3 = CredentialRecord.builder(UUID.randomUUID().toString(), "tenant-x", "cred-other",
                CredentialCategory.LLM, "anthropic").userId("user-other").ciphertext("c").iv("i").authTag("t").maskedPreview("m").build();

        repository.save(r1);
        repository.save(r2);
        repository.save(r3);

        List<CredentialRecord> userCreds = repository.findAllByUserId(targetUser);
        assertThat(userCreds).hasSize(2);
        assertThat(userCreds).extracting(CredentialRecord::name).containsExactlyInAnyOrder("cred-x", "cred-y");

        assertThat(repository.findAllByUserId(null)).isEmpty();
        assertThat(repository.findAllByUserId("  ")).isEmpty();
    }

    @Test
    @DisplayName("Default flag management and listing by tenant/user")
    void defaultFlagAndListing() {
        CredentialRecord r1 = CredentialRecord.builder(UUID.randomUUID().toString(), "tenant-1", "openai-1",
                CredentialCategory.LLM, "openai").isDefault(true).ciphertext("c1").iv("i1").authTag("t1").maskedPreview("m1").build();
        CredentialRecord r2 = CredentialRecord.builder(UUID.randomUUID().toString(), "tenant-1", "openai-2",
                CredentialCategory.LLM, "openai").isDefault(false).ciphertext("c2").iv("i2").authTag("t2").maskedPreview("m2").userId("user-abc").build();

        repository.save(r1);
        repository.save(r2);

        Optional<CredentialRecord> defaultOpt = repository.findDefaultByProvider("tenant-1", "openai");
        assertThat(defaultOpt).isPresent();
        assertThat(defaultOpt.get().name()).isEqualTo("openai-1");

        List<CredentialRecord> userList = repository.findByUserId("tenant-1", "user-abc");
        assertThat(userList).hasSize(1).extracting(CredentialRecord::name).containsExactly("openai-2");

        repository.deleteByName("tenant-1", "openai-1");
        assertThat(repository.findByName("tenant-1", "openai-1")).isEmpty();
    }
}
