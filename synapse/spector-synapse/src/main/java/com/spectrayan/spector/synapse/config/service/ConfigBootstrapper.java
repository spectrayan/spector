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
package com.spectrayan.spector.synapse.config.service;

import com.spectrayan.spector.synapse.config.model.ConfigCategory;
import com.spectrayan.spector.synapse.memory.MemoryRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Loads and applies saved configuration overrides from database on startup.
 * Registers a {@code NamespaceOpenListener} on {@link MemoryRegistry} to apply
 * dynamic scoped configuration overlays whenever a namespace is opened (ADR-0085).
 */
@Component
public class ConfigBootstrapper implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigBootstrapper.class);

    private final ConfigResolutionService resolutionService;
    private final ConfigApplicator applicator;
    private final ObjectProvider<MemoryRegistry> memoryRegistryProvider;
    private final ObjectProvider<com.spectrayan.spector.synapse.connector.service.CredentialService>
            credentialServiceProvider;

    public ConfigBootstrapper(ConfigResolutionService resolutionService,
                              ConfigApplicator applicator,
                              ObjectProvider<MemoryRegistry> memoryRegistryProvider) {
        this(resolutionService, applicator, memoryRegistryProvider, null);
    }

    /**
     * @param credentialServiceProvider the encrypted credentials store, optional — absent in embedded use
     */
    @org.springframework.beans.factory.annotation.Autowired
    public ConfigBootstrapper(ConfigResolutionService resolutionService,
                              ConfigApplicator applicator,
                              ObjectProvider<MemoryRegistry> memoryRegistryProvider,
                              ObjectProvider<com.spectrayan.spector.synapse.connector.service.CredentialService>
                                      credentialServiceProvider) {
        this.resolutionService = resolutionService;
        this.applicator = applicator;
        this.memoryRegistryProvider = memoryRegistryProvider;
        this.credentialServiceProvider = credentialServiceProvider;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("Bootstrapping configurations: applying defaults/overrides to shared/default namespace...");
        for (ConfigCategory category : ConfigCategory.values()) {
            try {
                Map<String, Object> effective = resolutionService.resolve("default", "default", category);
                applicator.apply("default", "default", category, effective);
                log.debug("Successfully bootstrapped configuration for category: {}", category.key());
            } catch (Exception e) {
                log.error("Failed to bootstrap configuration for category: {}", category.key(), e);
            }
        }

        MemoryRegistry memoryRegistry = memoryRegistryProvider.getIfAvailable();
        if (memoryRegistry != null && memoryRegistry.namespaceResolver() != null) {
            // Per-namespace embedding resolution happens at build time, before the open listener below
            // fires. The listener can only overlay live categories onto an already-dimensioned memory,
            // which is too late to choose an embedding model.
            memoryRegistry.namespaceResolver().setEmbeddingConfigResolver(
                    (tenantId, namespaceId) -> resolveEmbeddingConfig(tenantId, namespaceId));

            // The other half of scoped LLM overrides. ConfigApplicator stopped activating them in the
            // process-wide ProviderRegistry because that registry holds one active-generation name and
            // changed the LLM for every namespace; without this resolver a scoped override took effect
            // nowhere instead of everywhere.
            memoryRegistry.namespaceResolver().setLlmConfigResolver(
                    (tenantId, namespaceId) -> resolveLlmConfig(tenantId, namespaceId));

            memoryRegistry.namespaceResolver().addOpenListener((tenantId, namespaceId, memory) -> {
                log.debug("[ConfigBootstrapper] Overlaying configurations for opened namespace ns={}, tenant={}",
                        namespaceId, tenantId);
                for (ConfigCategory category : ConfigCategory.values()) {
                    try {
                        Map<String, Object> effective = resolutionService.resolve(tenantId, namespaceId, category);
                        applicator.applyToMemory(memory, category, effective);
                    } catch (Exception e) {
                        log.warn("[ConfigBootstrapper] Failed to apply config for category {} on opened namespace {}: {}",
                                category.key(), namespaceId, e.getMessage());
                    }
                }
            });
            log.info("[ConfigBootstrapper] Registered NamespaceOpenListener with MemoryRegistry");
        }
    }

    /**
     * Resolves the effective embedding provider configuration for one namespace.
     *
     * <p>Returns {@code null} when the namespace has no scoped override, so the resolver falls back to
     * the process default rather than rebuilding an identical provider.</p>
     *
     * <p>{@code namespaceId} is passed in {@code ConfigResolutionService}'s user slot, matching what the
     * open listener below already does. That conflates user and namespace scope in the
     * {@code user:<tenant>:<id>} key; the conflation is pre-existing and deliberately left alone here.</p>
     */
    private com.spectrayan.spector.provider.ProviderConfig resolveEmbeddingConfig(String tenantId, String namespaceId) {
        Map<String, Object> values = resolutionService.resolve(tenantId, namespaceId,
                ConfigCategory.EMBEDDING_PROVIDER);
        if (values == null || values.isEmpty()) {
            return null;
        }
        boolean scoped = resolutionService.hasScopedOverride(tenantId, namespaceId,
                ConfigCategory.EMBEDDING_PROVIDER);
        if (!scoped) {
            // No tenant or namespace override: the resolved map is just the system defaults, which is what
            // the process-default embedder was already built from.
            return null;
        }
        String provider = stringValue(values, "provider", null);
        if (provider == null || provider.isBlank()) {
            return null;
        }
        int dimensions = intValue(values, "dimensions", 0);
        var properties = new java.util.LinkedHashMap<String, String>();
        values.forEach((k, v) -> {
            if (v != null && !"provider".equals(k) && !"model".equals(k)
                    && !"base-url".equals(k) && !"dimensions".equals(k)) {
                properties.put(k, v.toString());
            }
        });
        return new com.spectrayan.spector.provider.ProviderConfig(
                provider,
                provider,
                stringValue(values, "model", ""),
                resolveCredential(tenantId, values),
                stringValue(values, "base-url", ""),
                Math.max(dimensions, 0),
                properties);
    }

    /**
     * Resolves the effective LLM provider configuration for one namespace.
     *
     * <p>Returns {@code null} when the namespace has no scoped override, so the resolver falls back to the
     * process-wide LLM bean rather than building a second identical provider.</p>
     */
    private com.spectrayan.spector.provider.ProviderConfig resolveLlmConfig(String tenantId, String namespaceId) {
        if (!resolutionService.hasScopedOverride(tenantId, namespaceId, ConfigCategory.LLM_PROVIDER)) {
            return null;
        }
        Map<String, Object> values = resolutionService.resolve(tenantId, namespaceId,
                ConfigCategory.LLM_PROVIDER);
        if (values == null || values.isEmpty()) {
            return null;
        }
        String provider = stringValue(values, "provider", null);
        if (provider == null || provider.isBlank()) {
            return null;
        }
        var properties = new java.util.LinkedHashMap<String, String>();
        values.forEach((k, v) -> {
            if (v != null && !"provider".equals(k) && !"model".equals(k) && !"base-url".equals(k)
                    && !ConfigResolutionService.CREDENTIAL_REF_KEY.equals(k)
                    && !ConfigResolutionService.FORBIDDEN_VALUE_KEYS.contains(k)) {
                properties.put(k, v.toString());
            }
        });
        return new com.spectrayan.spector.provider.ProviderConfig(
                provider,
                provider,
                stringValue(values, "model", ""),
                resolveCredential(tenantId, values),
                stringValue(values, "base-url", ""),
                0,
                properties);
    }

    /**
     * Resolves a provider's secret from the encrypted credentials store.
     *
     * <p>Configuration carries only {@code credential-ref}. The secret is decrypted here, under a per-tenant
     * derived key, and handed straight to the provider — it never enters a config row or a log line. An empty
     * result is correct for local providers such as Ollama, which need no credential.</p>
     */
    private String resolveCredential(String tenantId, Map<String, Object> values) {
        String ref = stringValue(values, ConfigResolutionService.CREDENTIAL_REF_KEY, "");
        if (ref == null || ref.isBlank()) {
            return "";
        }
        var credentials = credentialServiceProvider != null ? credentialServiceProvider.getIfAvailable() : null;
        if (credentials == null) {
            log.warn("[ConfigBootstrapper] configuration references credential '{}' but no CredentialService "
                    + "is available; the provider will be built without a key", ref);
            return "";
        }
        try {
            return credentials.resolveSecret(ref, tenantId).orElse("");
        } catch (RuntimeException e) {
            // Log the exception type only: the message could carry decrypted material.
            log.warn("[ConfigBootstrapper] could not resolve credential '{}' for tenant '{}': {}",
                    ref, tenantId, e.getClass().getSimpleName());
            return "";
        }
    }

    private static String stringValue(Map<String, Object> values, String key, String fallback) {
        Object value = values.get(key);
        return value != null ? value.toString() : fallback;
    }

    private static int intValue(Map<String, Object> values, String key, int fallback) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString().trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
