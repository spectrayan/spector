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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.spectrayan.spector.memory.kernel.identity.IdentityBundle;
import com.spectrayan.spector.synapse.config.SynapseProperties;

/**
 * Process pin table for open {@link IdentityBundle} instances (ADR-0029 §23.7, §25).
 *
 * <p>Pins up to 256 accounts and 32 tenants using a Caffeine-backed LRU with reference-counted
 * pin handles so active readers/writers never encounter an unmapped memory segment on eviction.
 * Identity bundles map only 1 FD each and <strong>do not</strong> count against the data-plane
 * {@code maxHotNamespaces} quota.</p>
 */
@Component
public class IdentityCache implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(IdentityCache.class);

    public static final int MAX_HOT_ACCOUNTS = 256;
    public static final int MAX_HOT_TENANTS = 32;

    private final Path dataDir;
    private final Cache<String, PinnedBundle> accountBundles;
    private final Cache<String, PinnedBundle> tenantBundles;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Autowired
    public IdentityCache(SynapseProperties properties) {
        this(properties.dataDir() != null ? Path.of(properties.dataDir()) : Path.of("./spector-data"));
    }

    public IdentityCache(Path dataDir) {
        this.dataDir = dataDir;
        this.accountBundles = Caffeine.newBuilder()
                .maximumSize(MAX_HOT_ACCOUNTS)
                .removalListener((String key, PinnedBundle pinned, RemovalCause cause) -> {
                    if (pinned != null) {
                        pinned.markEvicted();
                    }
                })
                .build();

        this.tenantBundles = Caffeine.newBuilder()
                .maximumSize(MAX_HOT_TENANTS)
                .removalListener((String key, PinnedBundle pinned, RemovalCause cause) -> {
                    if (pinned != null) {
                        pinned.markEvicted();
                    }
                })
                .build();
    }

    public IdentityHandle openAccount(String accountId) {
        ensureOpen();
        PinnedBundle pinned = accountBundles.get(accountId, id -> {
            Path path = IdentityPaths.accountIdentityBundle(dataDir, id);
            log.debug("[IdentityCache] Opening account identity bundle at {}", path);
            IdentityBundle bundle = IdentityBundle.open(path, true);
            return new PinnedBundle(bundle);
        });
        return pinned != null ? pinned.acquire() : null;
    }

    public IdentityHandle openTenant(String tenantId) {
        ensureOpen();
        PinnedBundle pinned = tenantBundles.get(tenantId, id -> {
            Path path = IdentityPaths.tenantIdentityBundle(dataDir, id);
            log.debug("[IdentityCache] Opening tenant identity bundle at {}", path);
            IdentityBundle bundle = IdentityBundle.open(path, true);
            return new PinnedBundle(bundle);
        });
        return pinned != null ? pinned.acquire() : null;
    }

    public void invalidateAccount(String accountId) {
        accountBundles.invalidate(accountId);
    }

    public void invalidateTenant(String tenantId) {
        tenantBundles.invalidate(tenantId);
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
            accountBundles.invalidateAll();
            accountBundles.cleanUp();
            tenantBundles.invalidateAll();
            tenantBundles.cleanUp();
        }
    }

    public static final class PinnedBundle {
        private final IdentityBundle bundle;
        private final AtomicInteger pinCount = new AtomicInteger(0);
        private final AtomicBoolean evicted = new AtomicBoolean(false);

        public PinnedBundle(IdentityBundle bundle) {
            this.bundle = bundle;
        }

        public IdentityBundle bundle() {
            return bundle;
        }

        public IdentityHandle acquire() {
            pinCount.incrementAndGet();
            return new IdentityHandle(this);
        }

        void release() {
            if (pinCount.decrementAndGet() == 0 && evicted.get()) {
                closeBundle();
            }
        }

        void markEvicted() {
            evicted.set(true);
            if (pinCount.get() == 0) {
                closeBundle();
            }
        }

        private void closeBundle() {
            try {
                bundle.close();
            } catch (Exception e) {
                log.warn("[IdentityCache] Error closing bundle on eviction: {}", e.getMessage());
            }
        }
    }

    public static final class IdentityHandle implements AutoCloseable {
        private final PinnedBundle pinned;
        private final AtomicBoolean closedHandle = new AtomicBoolean(false);

        public IdentityHandle(PinnedBundle pinned) {
            this.pinned = pinned;
        }

        public IdentityBundle bundle() {
            return pinned.bundle();
        }

        @Override
        public void close() {
            if (closedHandle.compareAndSet(false, true)) {
                pinned.release();
            }
        }
    }
}

