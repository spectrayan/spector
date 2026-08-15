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
                .generateUniqueName(true)
                .build();

        JdbcClient jdbc = JdbcClient.create(dataSource);
        ObjectMapper mapper = new ObjectMapper();
        repository = new JdbcCredentialRepository(jdbc, mapper);
    }

    @Test
    @DisplayName("Save and find credential record in repository")
    void saveAndFindRecord() {
        CredentialRecord record = CredentialRecord.builder(
                UUID.randomUUID().toString(), "tenant-1", "slack-key",
                CredentialCategory.CHANNEL, "slack")
                .ciphertext("encrypted-payload")
                .iv("iv-bytes")
                .authTag("tag-bytes")
                .maskedPreview("xoxb-••••••••1234")
                .properties(Map.of("channel", "general"))
                .isDefault(true)
                .build();

        CredentialRecord saved = repository.save(record);
        assertThat(saved.name()).isEqualTo("slack-key");

        Optional<CredentialRecord> found = repository.findByName("tenant-1", "slack-key");
        assertThat(found).isPresent();
        assertThat(found.get().isDefault()).isTrue();
        assertThat(found.get().properties()).containsEntry("channel", "general");
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
