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

import com.spectrayan.spector.synapse.connector.service.CredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcEncryptedCredentialProviderTest {

    private CredentialService credentialService;
    private JdbcEncryptedCredentialProvider provider;

    @BeforeEach
    void setUp() {
        credentialService = mock(CredentialService.class);
        provider = new JdbcEncryptedCredentialProvider(credentialService);
    }

    @Test
    @DisplayName("resolve with tenantId delegates to CredentialService.resolveSecret")
    void resolveDelegatesWithTenant() {
        when(credentialService.resolveSecret("slack-token", "tenant-1"))
                .thenReturn(Optional.of("xoxb-secret"));

        Optional<String> result = provider.resolve("slack-token", "tenant-1");
        assertThat(result).contains("xoxb-secret");
        verify(credentialService).resolveSecret("slack-token", "tenant-1");
    }

    @Test
    @DisplayName("resolve without tenantId delegates to default tenant")
    void resolveDelegatesDefaultTenant() {
        when(credentialService.resolveSecret("openai-key", "default"))
                .thenReturn(Optional.of("sk-proj-secret"));

        Optional<String> result = provider.resolve("openai-key");
        assertThat(result).contains("sk-proj-secret");
        verify(credentialService).resolveSecret("openai-key", "default");
    }
}
