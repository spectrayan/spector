/*
 * Copyright 2026 Spectrayan
 *
 * Licensed under the Business Source License 1.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://github.com/spectrayan/spector/blob/main/spector-memory/LICENSE
 *
 * Change Date: May 27, 2030
 * Change License: Apache License, Version 2.0
 */
package com.spectrayan.spector.memory.cortex;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Immutable handle to a single colocated partition (DISK mode).
 *
 * <p>Introduced for issue #443 (Phase 1). A handle bundles the per-partition
 * tier stores (via a {@link CognitiveMemoryRouter}) and the partition-scoped
 * {@code text.dat} store. Handles are held by {@code PartitionManager}'s
 * copy-on-write registry: the last (writable) handle is the active partition;
 * all earlier handles are frozen, read-only, and remain <em>open</em> so recall
 * can fan out across them (this closes the pre-#443 arena/mmap leak).</p>
 *
 * <h3>Lifecycle</h3>
 * <p>{@link #close()} releases the three partition-scoped tier stores (episodic,
 * semantic, procedural) plus the partition {@code text.dat}. It deliberately does
 * <b>not</b> close working memory — working memory is global (shared by every
 * router) and is closed exactly once by the owning component.</p>
 *
 * @param seq      the partition sequence number (matches the {@code NNN_epoch} dir)
 * @param dir      the partition directory (null in IN_MEMORY mode)
 * @param router   the tier-store router for this partition
 * @param text     the partition-scoped text store (null in IN_MEMORY mode)
 * @param writable {@code true} for the single active partition, {@code false} for frozen
 */
public record PartitionHandle(int seq, Path dir, CognitiveMemoryRouter router,
                              TextAppendMemory text, boolean writable)
        implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PartitionHandle.class);

    /** Returns a frozen (read-only) copy of this handle wrapping the same open stores. */
    public PartitionHandle asFrozen() {
        return writable ? new PartitionHandle(seq, dir, router, text, false) : this;
    }

    /**
     * Closes the partition-scoped stores (episodic, semantic, procedural) and the
     * partition {@code text.dat}. Working memory is global and is never closed here.
     */
    @Override
    public void close() {
        if (router != null) {
            closeQuietly(router.episodic());
            closeQuietly(router.semantic());
            closeQuietly(router.procedural());
        }
        closeQuietly(text);
    }

    private static void closeQuietly(AutoCloseable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception e) {
            log.debug("Failed to close partition-scoped store: {}", e.getMessage());
        }
    }
}
