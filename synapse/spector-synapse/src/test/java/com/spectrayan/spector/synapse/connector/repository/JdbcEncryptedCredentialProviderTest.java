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
package com.spectrayan.spector.synapse.connector.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.synapse.connector.model.CredentialCategory;
import com.spectrayan.spector.synapse.connector.model.CredentialRecord;
import com.spectrayan.spector.synapse.connector.model.CredentialType;
import com.spectrayan.spector.synapse.security.crypto.AesGcmCipher;
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

import static org.assertj.core.api.Assertions.assertThat;

class JdbcEncryptedCredentialProviderTest {

    private JdbcEncryptedCredentialProvider provider;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:db/migration/V5__credentials.sql")
                .generateUniqueName(true)
                .build();

        JdbcClient jdbc = JdbcClient.create(dataSource);
        ObjectMapper mapper = new ObjectMapper();
        AesGcmCipher cipher = new AesGcmCipher("test-master-encryption-key-32b-long");

        provider = new JdbcEncryptedCredentialProvider(jdbc, mapper, cipher);
    }

    @Test
    @DisplayName("Save and find credential by name with AES-GCM envelope encryption")
    void saveAndFindCredential() {
        CredentialRecord saved = provider.save(
                "tenant-alpha",
                "user-1",
                "prod-slack",
                CredentialCategory.CHANNEL,
                "slack",
                CredentialType.BEARER_TOKEN,
                "xoxb-secret-token-12345",
                Map.of("channel", "general"),
                true,
                "Primary Slack Bot",
                null
        );

        assertThat(saved.credentialId()).isNotBlank();
        assertThat(saved.name()).isEqualTo("prod-slack");
        assertThat(saved.provider()).isEqualTo("slack");
        assertThat(saved.category()).isEqualTo(CredentialCategory.CHANNEL);
        assertThat(saved.maskedPreview()).contains("••••••••");
        assertThat(saved.ciphertext()).isNotEqualTo("xoxb-secret-token-12345");

        // Verify findByName
        Optional<CredentialRecord> found = provider.findByName("tenant-alpha", "prod-slack");
        assertThat(found).isPresent();
        assertThat(found.get().isDefault()).isTrue();

        // Verify resolve returns decrypted plaintext
        Optional<String> decrypted = provider.resolve("prod-slack", "tenant-alpha");
        assertThat(decrypted).contains("xoxb-secret-token-12345");
    }

    @Test
    @DisplayName("Dynamic resolution by provider default when name is not specified")
    void resolveDefaultByProvider() {
        provider.save(
                "tenant-finance",
                null,
                "primary-openai",
                CredentialCategory.LLM,
                "openai",
                CredentialType.API_KEY,
                "sk-proj-super-secret-key-xyz",
                Map.of(),
                true,
                "Default OpenAI",
                null
        );

        // Resolve by provider name directly
        Optional<String> resolved = provider.resolve("openai", "tenant-finance");
        assertThat(resolved).contains("sk-proj-super-secret-key-xyz");
    }

    @Test
    @DisplayName("Tenant isolation: same secret name in different tenants remains distinct")
    void tenantIsolation() {
        provider.save("tenant-1", null, "db-pass", CredentialCategory.DATABASE, "postgres",
                CredentialType.CONNECTION_STRING, "pass-1", Map.of(), false, null, null);

        provider.save("tenant-2", null, "db-pass", CredentialCategory.DATABASE, "postgres",
                CredentialType.CONNECTION_STRING, "pass-2", Map.of(), false, null, null);

        assertThat(provider.resolve("db-pass", "tenant-1")).contains("pass-1");
        assertThat(provider.resolve("db-pass", "tenant-2")).contains("pass-2");
    }

    @Test
    @DisplayName("Delete credential invalidates cache and removes from database")
    void deleteCredential() {
        provider.save("tenant-1", null, "temp-key", CredentialCategory.LLM, "gemini",
                CredentialType.API_KEY, "temp-secret", Map.of(), false, null, null);

        assertThat(provider.resolve("temp-key", "tenant-1")).contains("temp-secret");

        boolean deleted = provider.deleteByName("tenant-1", "temp-key");
        assertThat(deleted).isTrue();

        assertThat(provider.findByName("tenant-1", "temp-key")).isEmpty();
        assertThat(provider.resolve("temp-key", "tenant-1")).isEmpty();
    }
}
