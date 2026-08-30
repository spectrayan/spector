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
package com.spectrayan.spector.synapse.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.spectrayan.spector.memory.SalienceProfileProvider;
import com.spectrayan.spector.memory.SpectorMemory;
import com.spectrayan.spector.provider.embedding.EmbeddingProvider;
import com.spectrayan.spector.provider.generation.LlmProvider;
import com.spectrayan.spector.synapse.catalog.AccountCatalog;
import com.spectrayan.spector.synapse.config.SynapseProperties;
import com.spectrayan.spector.synapse.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Resolves the {@link SpectorMemory} instance for the current request.
 *
 * <p>Since Phase 1 (ADR-0029), this class is a <strong>thin façade</strong> over
 * {@link NamespaceResolver}. The public API is preserved for backward compatibility:
 * all callers of {@code resolveForCurrentRequest()} and {@code resolveFor(userId)}
 * continue to work unchanged.</p>
 *
 * <h3>Resolution flow</h3>
 * <ul>
 *   <li>Auth disabled or anonymous principal ({@code userId == "default"}):
 *       returns the shared {@link SpectorMemory} bean (legacy behavior).</li>
 *   <li>Authenticated: delegates to {@link NamespaceResolver#resolve(String)},
 *       which mediates through the {@link AccountCatalog} to resolve
 *       {@code accountId → defaultNamespaceId → SpectorMemory}.</li>
 * </ul>
 *
 * <p>The hot cache is now keyed by {@code namespaceId} instead of {@code userId}.
 * Since {@code namespaceId == accountId == userId} for default namespaces, this is
 * a no-op change for all existing single-namespace users (ADR §6.3, Q7).</p>
 *
 * @see NamespaceResolver
 * @see AccountCatalog
 */
@Component
public final class MemoryRegistry implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MemoryRegistry.class);

    /** Literal user id used when the request is anonymous or auth is disabled. */
    private static final String DEFAULT_USER_ID = "default";

    private final ObjectProvider<SpectorMemory> sharedProvider;
    private final SynapseProperties synapseProps;
    private final NamespaceResolver resolver;

    /**
     * Test/non-Spring constructor that bypasses the catalog plane. When auth is enabled,
     * instances are built directly by {@code namespaceId == userId}, identical to the
     * pre-Phase-1 behavior. This preserves full backward compatibility for existing tests.
     */
    public MemoryRegistry(
            ObjectProvider<SpectorMemory> sharedProvider,
            SynapseProperties synapseProps,
            ObjectProvider<EmbeddingProvider> embedderProvider,
            ObjectProvider<LlmProvider> textGenProvider,
            ObjectProvider<SalienceProfileProvider> salienceProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            int maxInstances) {
        this.sharedProvider = sharedProvider;
        this.synapseProps = synapseProps;
        // Passthrough catalog: getOrCreateAccount returns an Account with defaultNamespaceId == accountId
        AccountCatalog passthrough = new PassthroughCatalog();
        this.resolver = new NamespaceResolver(
                passthrough, synapseProps, embedderProvider, textGenProvider, salienceProvider,
                objectMapperProvider, null, null, null, null, null, maxInstances);
        log.info("[MemoryRegistry] initialized (test mode): authEnabled={}, maxInstances={}",
                synapseProps.auth().enabled(), maxInstances);
    }

    @Autowired
    public MemoryRegistry(
            ObjectProvider<SpectorMemory> sharedProvider,
            SynapseProperties synapseProps,
            ObjectProvider<EmbeddingProvider> embedderProvider,
            ObjectProvider<LlmProvider> textGenProvider,
            ObjectProvider<SalienceProfileProvider> salienceProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            ObjectProvider<org.springframework.cache.CacheManager> cacheManagerProvider,
            ObjectProvider<com.spectrayan.spector.memory.DataEncryptor> encryptorProvider,
            ObjectProvider<io.micrometer.observation.ObservationRegistry> observationRegistryProvider,
            ObjectProvider<com.spectrayan.spector.config.ObservabilityConfig> observabilityConfigProvider,
            ObjectProvider<org.quartz.Scheduler> quartzSchedulerProvider,
            AccountCatalog accountCatalog,
            @Value("${spector.auth.memory.max-instances:512}") int maxInstances) {
        this.sharedProvider = sharedProvider;
        this.synapseProps = synapseProps;
        this.resolver = new NamespaceResolver(
                accountCatalog, synapseProps,
                embedderProvider, textGenProvider, salienceProvider,
                objectMapperProvider, cacheManagerProvider, encryptorProvider,
                observationRegistryProvider, observabilityConfigProvider,
                quartzSchedulerProvider, maxInstances);
        log.info("[MemoryRegistry] initialized: authEnabled={}, maxInstances={}, catalog={}",
                synapseProps.auth().enabled(), maxInstances, accountCatalog.getClass().getSimpleName());
    }

    /**
     * Resolves memory for the current request by reading the {@code SecurityContextHolder}.
     *
     * @return the single shared instance when auth is disabled or the principal is anonymous;
     *         otherwise the catalog-resolved namespace instance for the authenticated principal
     */
    public SpectorMemory resolveForCurrentRequest() {
        if (!synapseProps.auth().enabled()) {
            return sharedMemory();
        }
        // Check for bound MemoryBinding in RequestAttributes (Filter + RequestAttributes pattern, ADR §16)
        try {
            org.springframework.web.context.request.RequestAttributes attrs =
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                Object binding = attrs.getAttribute(MemoryBinding.ATTRIBUTE_KEY,
                        org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
                if (binding instanceof MemoryBinding mb && mb.memory() != null) {
                    return mb.memory();
                }
            }
        } catch (Exception ignored) {
            // Fall through to standard resolution
        }

        String userId = SecurityUtils.getUserId();
        if (DEFAULT_USER_ID.equals(userId)) {
            return sharedMemory();
        }
        return resolver.resolve(userId);
    }

    /**
     * Explicit resolution by principal id, for tests and non-servlet callers.
     *
     * @param userId the authenticated principal TSID; {@code "default"}, {@code null}, or blank
     *               resolves to the single shared instance
     * @return the shared instance for the anonymous/default principal, otherwise the
     *         catalog-resolved namespace instance
     */
    public SpectorMemory resolveFor(String userId) {
        if (userId == null || userId.isBlank() || DEFAULT_USER_ID.equals(userId)) {
            return sharedMemory();
        }
        return resolver.resolve(userId);
    }

    /**
     * Closes every cached namespace instance exactly once. The shared instance is never touched.
     */
    @Override
    public void close() {
        resolver.close();
        log.info("[MemoryRegistry] closed (delegated to NamespaceResolver)");
    }

    /** @return the number of currently-cached instances (test/observability helper). */
    public int cachedInstanceCount() {
        return resolver.cachedInstanceCount();
    }

    /** Returns a snapshot of all currently cached SpectorMemory instances for batch operations. */
    public java.util.List<SpectorMemory> cachedInstances() {
        return resolver.cachedInstances();
    }

    /** Returns the underlying NamespaceResolver (test/admin access). */
    public NamespaceResolver namespaceResolver() {
        return resolver;
    }

    // ══════════════════════════════════════════════════════════════
    // Internals
    // ══════════════════════════════════════════════════════════════

    private SpectorMemory sharedMemory() {
        return sharedProvider.getIfAvailable();
    }
}
