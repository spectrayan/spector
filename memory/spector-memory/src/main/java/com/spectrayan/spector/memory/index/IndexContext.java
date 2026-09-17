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
package com.spectrayan.spector.memory.index;

import com.spectrayan.spector.commons.concurrent.spi.SpectorExecutorProvider;
import com.spectrayan.spector.kernel.bundle.RuntimeBundle;
import com.spectrayan.spector.memory.cortex.index.MemoryIndex;
import com.spectrayan.spector.memory.persist.PartitionManager;

/**
 * Context provided to {@link ManagedIndex} instances during lifecycle attachment (ADR-0082).
 *
 * @param runtimeBundle Active runtime bundle slice catalog (null in purely in-memory mode).
 * @param partitionManager Active partition manager for episodic/semantic stores.
 * @param memoryIndex Shared memory index directory.
 * @param executorProvider System thread-plane executor provider.
 *
 * @since 1.1.0
 */
public record IndexContext(
        RuntimeBundle runtimeBundle,
        PartitionManager partitionManager,
        MemoryIndex memoryIndex,
        SpectorExecutorProvider executorProvider
) {}
