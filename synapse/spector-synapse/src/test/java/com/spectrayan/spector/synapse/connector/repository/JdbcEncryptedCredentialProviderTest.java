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
