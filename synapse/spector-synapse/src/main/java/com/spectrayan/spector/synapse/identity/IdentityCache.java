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
package com.spectrayan.spector.synapse.identity;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.spectrayan.spector.memory.identity.IdentityBundle;
import com.spectrayan.spector.synapse.config.SynapseProperties;

/**
 * Process cache for open {@link IdentityBundle} instances (ADR-0029 §23.7, §25).
 *
 * <p>Caches up to 256 accounts and 32 tenants. Identity bundles map only 1 FD each and
 * <strong>do not</strong> count against the data-plane {@code maxHotNamespaces} quota.</p>
 */
@Component
public class IdentityCache implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(IdentityCache.class);

    public static final int MAX_HOT_ACCOUNTS = 256;
    public static final int MAX_HOT_TENANTS = 32;

    private final Path dataDir;
    private final ConcurrentHashMap<String, IdentityBundle> accountBundles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IdentityBundle> tenantBundles = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    @org.springframework.beans.factory.annotation.Autowired
    public IdentityCache(SynapseProperties properties) {
        this.dataDir = properties.dataDir() != null ? Path.of(properties.dataDir()) : Path.of("./spector-data");
    }

    public IdentityCache(Path dataDir) {
        this.dataDir = dataDir;
    }

    private final Object evictionLock = new Object();
    private final java.util.concurrent.ConcurrentLinkedDeque<String> accountLru = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private final java.util.concurrent.ConcurrentLinkedDeque<String> tenantLru = new java.util.concurrent.ConcurrentLinkedDeque<>();

    public IdentityBundle getOrOpenAccount(String accountId) {
        ensureOpen();
        if (accountBundles.size() >= MAX_HOT_ACCOUNTS && !accountBundles.containsKey(accountId)) {
            evictOldestAccount();
        }
        accountLru.remove(accountId);
        accountLru.addLast(accountId);
        return accountBundles.computeIfAbsent(accountId, id -> {
            Path path = IdentityPaths.accountIdentityBundle(dataDir, id);
            log.debug("[IdentityCache] Opening account identity bundle at {}", path);
            return IdentityBundle.open(path, true);
        });
    }

    public IdentityBundle getOrOpenTenant(String tenantId) {
        ensureOpen();
        if (tenantBundles.size() >= MAX_HOT_TENANTS && !tenantBundles.containsKey(tenantId)) {
            evictOldestTenant();
        }
        tenantLru.remove(tenantId);
        tenantLru.addLast(tenantId);
        return tenantBundles.computeIfAbsent(tenantId, id -> {
            Path path = IdentityPaths.tenantIdentityBundle(dataDir, id);
            log.debug("[IdentityCache] Opening tenant identity bundle at {}", path);
            return IdentityBundle.open(path, true);
        });
    }

    public void invalidateAccount(String accountId) {
        accountLru.remove(accountId);
        IdentityBundle bundle = accountBundles.remove(accountId);
        if (bundle != null) {
            try {
                bundle.close();
            } catch (Exception e) {
                log.warn("[IdentityCache] Failed to close invalidated bundle for account {}: {}", accountId, e.getMessage());
            }
        }
    }

    private void evictOldestAccount() {
        synchronized (evictionLock) {
            while (accountBundles.size() >= MAX_HOT_ACCOUNTS) {
                String oldest = accountLru.pollFirst();
                if (oldest == null) break;
                invalidateAccount(oldest);
            }
        }
    }

    private void evictOldestTenant() {
        synchronized (evictionLock) {
            while (tenantBundles.size() >= MAX_HOT_TENANTS) {
                String oldest = tenantLru.pollFirst();
                if (oldest == null) break;
                tenantLru.remove(oldest);
                IdentityBundle bundle = tenantBundles.remove(oldest);
                if (bundle != null) {
                    try {
                        bundle.close();
                    } catch (Exception e) {
                        log.warn("[IdentityCache] Failed to close evicted tenant bundle {}: {}", oldest, e.getMessage());
                    }
                }
            }
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("IdentityCache is closed");
        }
    }

    @Override
    @PreDestroy
    public void close() {
        if (closed.compareAndSet(false, true)) {
            log.info("[IdentityCache] Closing all cached identity bundles");
            accountBundles.forEach((id, bundle) -> {
                try { bundle.close(); } catch (Exception e) {
                    log.warn("[IdentityCache] Error closing account bundle for {}: {}", id, e.getMessage());
                }
            });
            accountBundles.clear();
            tenantBundles.forEach((id, bundle) -> {
                try { bundle.close(); } catch (Exception e) {
                    log.warn("[IdentityCache] Error closing tenant bundle for {}: {}", id, e.getMessage());
                }
            });
            tenantBundles.clear();
        }
    }
}
