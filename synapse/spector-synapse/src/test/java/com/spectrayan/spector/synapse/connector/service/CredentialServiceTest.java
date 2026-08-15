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
package com.spectrayan.spector.synapse.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.synapse.connector.api.dto.CreateCredentialRequest;
import com.spectrayan.spector.synapse.connector.api.dto.UpdateCredentialRequest;
import com.spectrayan.spector.synapse.connector.model.CredentialCategory;
import com.spectrayan.spector.synapse.connector.model.CredentialRecord;
import com.spectrayan.spector.synapse.connector.model.CredentialType;
import com.spectrayan.spector.synapse.connector.repository.CredentialRepository;
import com.spectrayan.spector.synapse.connector.repository.JdbcCredentialRepository;
import com.spectrayan.spector.synapse.security.crypto.AesGcmCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialServiceTest {

    private CredentialService service;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:db/migration/V5__credentials.sql")
                .generateUniqueName(true)
                .build();

        JdbcClient jdbc = JdbcClient.create(dataSource);
        ObjectMapper mapper = new ObjectMapper();
        CredentialRepository repository = new JdbcCredentialRepository(jdbc, mapper);
        AesGcmCipher cipher = new AesGcmCipher("test-master-encryption-key-32b-long");

        service = new DefaultCredentialService(repository, cipher);
    }

    @Test
    @DisplayName("Create credential, encrypt payload, and resolve decrypted secret")
    void createAndResolveCredential() {
        CreateCredentialRequest req = new CreateCredentialRequest(
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

        CredentialRecord saved = service.createCredential("tenant-alpha", "user-1", req);

        assertThat(saved.credentialId()).isNotBlank();
        assertThat(saved.name()).isEqualTo("prod-slack");
        assertThat(saved.provider()).isEqualTo("slack");
        assertThat(saved.maskedPreview()).contains("••••••••");
        assertThat(saved.ciphertext()).isNotEqualTo("xoxb-secret-token-12345");

        // Verify getCredential
        Optional<CredentialRecord> found = service.getCredential("tenant-alpha", "prod-slack");
        assertThat(found).isPresent();
        assertThat(found.get().isDefault()).isTrue();

        // Verify resolveSecret
        Optional<String> decrypted = service.resolveSecret("prod-slack", "tenant-alpha");
        assertThat(decrypted).contains("xoxb-secret-token-12345");
    }

    @Test
    @DisplayName("Update credential preserves secret if omitted")
    void updatePreservesSecret() {
        CreateCredentialRequest req = new CreateCredentialRequest(
                "my-db", CredentialCategory.DATABASE, "postgres",
                CredentialType.CONNECTION_STRING, "postgres://user:supersecret@db:5432/main",
                null, false, "Initial", null);

        service.createCredential("tenant-1", null, req);

        UpdateCredentialRequest updateReq = new UpdateCredentialRequest(
                CredentialCategory.DATABASE, "postgres", CredentialType.CONNECTION_STRING,
                null, null, true, "Updated Description", null);

        Optional<CredentialRecord> updated = service.updateCredential("tenant-1", null, "my-db", updateReq);
        assertThat(updated).isPresent();
        assertThat(updated.get().description()).isEqualTo("Updated Description");
        assertThat(updated.get().isDefault()).isTrue();

        // Secret should still be resolvable to original plaintext
        assertThat(service.resolveSecret("my-db", "tenant-1"))
                .contains("postgres://user:supersecret@db:5432/main");
    }

    @Test
    @DisplayName("Test credential probe returns SUCCESS")
    void testCredentialProbe() {
        CreateCredentialRequest req = new CreateCredentialRequest(
                "openai-key", CredentialCategory.LLM, "openai",
                CredentialType.API_KEY, "sk-proj-super-secret-key-12345", null, true, null, null);

        service.createCredential("tenant-1", null, req);

        Map<String, Object> testResult = service.testCredential("tenant-1", "openai-key");
        assertThat(testResult).containsEntry("status", "SUCCESS");
        assertThat(testResult).containsEntry("provider", "openai");
    }
}
