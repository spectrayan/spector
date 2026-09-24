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
package com.spectrayan.spector.synapse.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.synapse.config.model.ConfigCategory;
import com.spectrayan.spector.synapse.config.model.ScopedConfig;
import com.spectrayan.spector.synapse.config.repository.ConfigRepository;
import com.spectrayan.spector.synapse.config.service.ConfigResolutionService;

/**
 * Verifies that provider secrets cannot be persisted in scoped configuration.
 *
 * <p>The previous arrangement put the raw {@code api-key} into the {@code scoped_config} values JSON and
 * masked it on read. That is the more dangerous of the two possible mistakes, because the API response looked
 * handled while the database column held cleartext. The encrypted {@code credentials} table already existed
 * and was unused for providers.</p>
 */
@DisplayName("Credential Reference Instead of Cleartext Secret")
class CredentialReferenceTest {

    private ConfigRepository repository;
    private ConfigResolutionService service;

    @BeforeEach
    void setUp() {
        repository = mock(ConfigRepository.class);
        when(repository.get(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(ConfigCategory.class)))
                .thenReturn(java.util.Optional.empty());
        service = new ConfigResolutionService(repository);
    }

    private static ScopedConfig scopedConfig(Map<String, Object> values) {
        return new ScopedConfig("tenant:acme", ConfigCategory.LLM_PROVIDER, values,
                Instant.now(), "tester");
    }

    @Test
    @DisplayName("saving an override that carries an api-key is refused")
    void apiKeyIsRefusedOnSave() {
        var values = new LinkedHashMap<String, Object>();
        values.put("provider", "openai");
        values.put("api-key", "fixture-credential-value");

        assertThatThrownBy(() -> service.saveOverride(scopedConfig(values)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("api-key")
                .hasMessageContaining("cleartext")
                .hasMessageContaining(ConfigResolutionService.CREDENTIAL_REF_KEY);
    }

    @Test
    @DisplayName("every secret-shaped key is refused, not just api-key")
    void allSecretShapedKeysAreRefused() {
        for (String key : ConfigResolutionService.FORBIDDEN_VALUE_KEYS) {
            var values = new LinkedHashMap<String, Object>();
            values.put("provider", "openai");
            values.put(key, "fixture-credential-value");

            assertThatThrownBy(() -> service.saveOverride(scopedConfig(values)))
                    .as("key '%s' must be refused", key)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("saving an override that references a credential is allowed")
    void credentialReferenceIsAllowed() {
        var values = new LinkedHashMap<String, Object>();
        values.put("provider", "openai");
        values.put(ConfigResolutionService.CREDENTIAL_REF_KEY, "acme-openai");

        assertThatCode(() -> service.saveOverride(scopedConfig(values))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the resolved LLM defaults expose a credential reference, never a key")
    void llmDefaultsCarryNoSecret() {
        Map<String, Object> resolved = service.resolve(null, null, ConfigCategory.LLM_PROVIDER);

        assertThat(resolved).containsKey(ConfigResolutionService.CREDENTIAL_REF_KEY);
        ConfigResolutionService.FORBIDDEN_VALUE_KEYS
                .forEach(forbidden -> assertThat(resolved).doesNotContainKey(forbidden));
    }

    @Test
    @DisplayName("the embedding defaults carry no secret either")
    void embeddingDefaultsCarryNoSecret() {
        Map<String, Object> resolved = service.resolve(null, null, ConfigCategory.EMBEDDING_PROVIDER);

        ConfigResolutionService.FORBIDDEN_VALUE_KEYS
                .forEach(forbidden -> assertThat(resolved).doesNotContainKey(forbidden));
    }

    @Test
    @DisplayName("the credentials store has a category for embedding providers")
    void credentialCategoryCoversEmbedding() {
        // Without this, an embedding provider's key had no legitimate home and could only have been put
        // somewhere it does not belong.
        assertThat(com.spectrayan.spector.synapse.connector.model.CredentialCategory.valueOf("EMBEDDING"))
                .isNotNull();
    }
}
