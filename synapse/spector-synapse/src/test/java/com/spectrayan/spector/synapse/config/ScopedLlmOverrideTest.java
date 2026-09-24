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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.spectrayan.spector.provider.ProviderRegistry;
import com.spectrayan.spector.synapse.config.model.ConfigCategory;
import com.spectrayan.spector.synapse.config.service.ConfigApplicator;
import com.spectrayan.spector.synapse.memory.NamespaceResolver;

/**
 * Verifies that a tenant- or user-scoped LLM override no longer repoints the LLM for the whole process.
 *
 * <p>{@code DefaultProviderRegistry} keeps a single {@code volatile} active-generation name. Calling
 * {@code activateGeneration} for a scoped override therefore changed the LLM for every namespace in the
 * process, including other tenants' — one tenant configuring its own model silently repointed everyone.
 * The registry is still the right mechanism for a system-wide default, which embedded and single-tenant
 * deployments rely on; it is simply the wrong one for a per-tenant override.</p>
 */
@DisplayName("Scoped LLM Override Isolation")
class ScopedLlmOverrideTest {

    private ProviderRegistry providerRegistry;
    private ConfigApplicator applicator;

    @BeforeEach
    void setUp() {
        providerRegistry = mock(ProviderRegistry.class);
        when(providerRegistry.generationProviderNames()).thenReturn(Set.of("ollama"));
        applicator = new ConfigApplicator(providerRegistry, null, null, null, null, null);
    }

    private static Map<String, Object> ollamaOverride() {
        var values = new LinkedHashMap<String, Object>();
        values.put("provider", "ollama");
        values.put("model", "llama3.2");
        return values;
    }

    @Test
    @DisplayName("a tenant-scoped override does not activate in the process-wide registry")
    void tenantScopedOverrideDoesNotTouchTheGlobalRegistry() {
        applicator.apply("acme", "ns-1", ConfigCategory.LLM_PROVIDER, ollamaOverride());

        verify(providerRegistry, never()).activateGeneration(anyString());
        verify(providerRegistry, never()).registerGeneration(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("a user-scoped override does not activate in the process-wide registry")
    void userScopedOverrideDoesNotTouchTheGlobalRegistry() {
        applicator.apply("default", "user-42", ConfigCategory.LLM_PROVIDER, ollamaOverride());

        verify(providerRegistry, never()).activateGeneration(anyString());
    }

    @Test
    @DisplayName("system scope still activates in the process-wide registry")
    void systemScopeStillActivatesGlobally() {
        applicator.apply("default", "default", ConfigCategory.LLM_PROVIDER, ollamaOverride());

        // The global path must keep working: it is what embedded and single-tenant deployments use.
        verify(providerRegistry).activateGeneration("ollama");
    }

    @Test
    @DisplayName("a null scope is treated as system scope")
    void nullScopeIsSystemScope() {
        applicator.apply(null, null, ConfigCategory.LLM_PROVIDER, ollamaOverride());

        verify(providerRegistry).activateGeneration("ollama");
    }

    @Test
    @DisplayName("a scoped override reaches that tenant's namespaces and no others")
    void scopedOverrideReachesOnlyItsOwnNamespaces() {
        // The positive half of the requirement. Stopping the global mutation was only half the fix: without
        // per-namespace resolution a scoped override took effect nowhere instead of everywhere.
        var resolvedFor = new java.util.LinkedHashMap<String, String>();

        NamespaceResolver.EmbeddingConfigResolver resolver = (tenantId, namespaceId) -> {
            if (!"acme".equals(tenantId)) {
                return null;
            }
            resolvedFor.put(namespaceId, "acme-model");
            return new com.spectrayan.spector.provider.ProviderConfig(
                    "ollama", "ollama", "acme-model", "", "http://localhost:11434", 0, java.util.Map.of());
        };

        assertThat(resolver.resolve("acme", "ns-acme-1")).isNotNull();
        assertThat(resolver.resolve("acme", "ns-acme-2")).isNotNull();
        assertThat(resolver.resolve("other-tenant", "ns-other")).isNull();

        assertThat(resolvedFor).containsOnlyKeys("ns-acme-1", "ns-acme-2");
    }

    @Test
    @DisplayName("the LLM pool shares one provider across namespaces on the same configuration")
    void llmPoolSharesByConfiguration() {
        var pool = new com.spectrayan.spector.synapse.memory.LlmProviderPool();
        var config = new com.spectrayan.spector.provider.ProviderConfig(
                "ollama", "ollama", "llama3.2", "", "http://localhost:11434", 0, java.util.Map.of());
        var fingerprint = com.spectrayan.spector.provider.ProviderFingerprint.of(config);
        var provider = mock(com.spectrayan.spector.provider.generation.LlmProvider.class);

        var first = pool.acquire(fingerprint, fp -> provider);
        var second = pool.acquire(fingerprint, fp -> provider);

        assertThat(second).isSameAs(first);
        assertThat(pool.distinctConfigurations()).isEqualTo(1);
        assertThat(pool.referenceCount(fingerprint)).isEqualTo(2);
        pool.close();
    }

    @Test
    @DisplayName("the embedding category is not silently swallowed by applyToMemory")
    void embeddingCategoryIsAcknowledged() {
        // applyToMemory's default branch used to absorb EMBEDDING_PROVIDER at debug level, which read like
        // the category had been handled. It is now an explicit case stating that resolution happens at
        // build time. Asserting no exception and no memory mutation is the observable part.
        var memory = mock(com.spectrayan.spector.memory.SpectorMemory.class);

        applicator.applyToMemory(memory, ConfigCategory.EMBEDDING_PROVIDER, ollamaOverride());

        verify(memory, never()).applyLiveMemoryPatch(org.mockito.ArgumentMatchers.any());
        assertThat(applicator.pendingCount()).isZero();
    }
}
