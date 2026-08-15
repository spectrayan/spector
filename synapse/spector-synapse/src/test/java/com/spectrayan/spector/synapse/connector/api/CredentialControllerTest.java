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
package com.spectrayan.spector.synapse.connector.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrayan.spector.synapse.connector.api.dto.CreateCredentialRequest;
import com.spectrayan.spector.synapse.connector.api.dto.UpdateCredentialRequest;
import com.spectrayan.spector.synapse.connector.model.CredentialCategory;
import com.spectrayan.spector.synapse.connector.model.CredentialType;
import com.spectrayan.spector.synapse.connector.repository.CredentialRepository;
import com.spectrayan.spector.synapse.connector.repository.JdbcCredentialRepository;
import com.spectrayan.spector.synapse.connector.service.CredentialService;
import com.spectrayan.spector.synapse.connector.service.DefaultCredentialService;
import com.spectrayan.spector.synapse.security.crypto.AesGcmCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CredentialControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:db/migration/V5__credentials.sql")
                .generateUniqueName(true)
                .build();

        JdbcClient jdbc = JdbcClient.create(dataSource);
        objectMapper = new ObjectMapper();
        CredentialRepository repository = new JdbcCredentialRepository(jdbc, objectMapper);
        AesGcmCipher cipher = new AesGcmCipher("test-master-encryption-key-32b-long");
        CredentialService service = new DefaultCredentialService(repository, cipher);
        CredentialController controller = new CredentialController(service);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("POST /api/v1/credentials creates and returns masked preview (never raw secret)")
    void createCredentialEndpoint() throws Exception {
        CreateCredentialRequest req = new CreateCredentialRequest(
                "prod-whatsapp",
                CredentialCategory.CHANNEL,
                "whatsapp",
                CredentialType.BEARER_TOKEN,
                "EAAG...super-secret-meta-whatsapp-token-12345",
                Map.of("phoneId", "1234567890"),
                true,
                "WhatsApp Customer Alerts",
                null
        );

        mockMvc.perform(post("/api/v1/credentials")
                        .header("X-Tenant-ID", "tenant-sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("prod-whatsapp")))
                .andExpect(jsonPath("$.provider", is("whatsapp")))
                .andExpect(jsonPath("$.category", is("CHANNEL")))
                .andExpect(jsonPath("$.isDefault", is(true)))
                .andExpect(jsonPath("$.maskedPreview", containsString("••••••••")));
    }

    @Test
    @DisplayName("GET /api/v1/credentials lists credentials with masked previews")
    void listCredentialsEndpoint() throws Exception {
        CreateCredentialRequest req1 = new CreateCredentialRequest(
                "openai-primary", CredentialCategory.LLM, "openai",
                CredentialType.API_KEY, "sk-proj-1234567890abcdef", null, true, null, null);
        CreateCredentialRequest req2 = new CreateCredentialRequest(
                "gemini-backup", CredentialCategory.LLM, "gemini",
                CredentialType.API_KEY, "AIzaSy-9876543210zyxwvu", null, false, null, null);

        mockMvc.perform(post("/api/v1/credentials")
                .header("X-Tenant-ID", "tenant-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)));

        mockMvc.perform(post("/api/v1/credentials")
                .header("X-Tenant-ID", "tenant-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)));

        mockMvc.perform(get("/api/v1/credentials")
                        .header("X-Tenant-ID", "tenant-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].maskedPreview", containsString("••••••••")))
                .andExpect(jsonPath("$[1].maskedPreview", containsString("••••••••")));
    }

    @Test
    @DisplayName("PUT /api/v1/credentials/{name} updates metadata and preserves or rotates secret")
    void updateCredentialEndpoint() throws Exception {
        CreateCredentialRequest req = new CreateCredentialRequest(
                "slack-ops", CredentialCategory.CHANNEL, "slack",
                CredentialType.BEARER_TOKEN, "xoxb-initial-token-1234", null, false, "Initial", null);

        mockMvc.perform(post("/api/v1/credentials")
                .header("X-Tenant-ID", "tenant-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));

        UpdateCredentialRequest updateReq = new UpdateCredentialRequest(
                CredentialCategory.CHANNEL, "slack", CredentialType.BEARER_TOKEN,
                null, Map.of("updated", true), true, "Updated Description", null);

        mockMvc.perform(put("/api/v1/credentials/slack-ops")
                        .header("X-Tenant-ID", "tenant-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault", is(true)))
                .andExpect(jsonPath("$.description", is("Updated Description")));
    }

    @Test
    @DisplayName("DELETE /api/v1/credentials/{name} purges credential")
    void deleteCredentialEndpoint() throws Exception {
        CreateCredentialRequest req = new CreateCredentialRequest(
                "to-delete", CredentialCategory.DATABASE, "postgres",
                CredentialType.CONNECTION_STRING, "postgres://user:pass@host:5432/db", null, false, null, null);

        mockMvc.perform(post("/api/v1/credentials")
                .header("X-Tenant-ID", "tenant-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));

        mockMvc.perform(delete("/api/v1/credentials/to-delete")
                        .header("X-Tenant-ID", "tenant-1"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/credentials/to-delete")
                        .header("X-Tenant-ID", "tenant-1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/credentials/{name}/test validates connectivity and decryption")
    void testCredentialEndpoint() throws Exception {
        CreateCredentialRequest req = new CreateCredentialRequest(
                "probe-key", CredentialCategory.LLM, "openai",
                CredentialType.API_KEY, "sk-test-valid-key-xyz", null, false, null, null);

        mockMvc.perform(post("/api/v1/credentials")
                .header("X-Tenant-ID", "tenant-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));

        mockMvc.perform(post("/api/v1/credentials/probe-key/test")
                        .header("X-Tenant-ID", "tenant-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.name", is("probe-key")))
                .andExpect(jsonPath("$.provider", is("openai")));
    }
}
