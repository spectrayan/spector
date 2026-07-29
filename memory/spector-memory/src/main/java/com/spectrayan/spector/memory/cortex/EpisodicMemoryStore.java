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

import java.nio.file.Path;

/**
 * Backward compatibility subclass for {@link EpisodicPartitionedMemory}.
 */
public class EpisodicMemoryStore extends EpisodicPartitionedMemory {

    public EpisodicMemoryStore(int quantizedVecBytes, int capacity) {
        super(quantizedVecBytes, capacity);
    }

    public EpisodicMemoryStore(int quantizedVecBytes, int capacity, Path filePath) {
        super(quantizedVecBytes, capacity, filePath);
    }

    public EpisodicMemoryStore(Path filePath, int quantizedVecBytes, int capacity) {
        super(filePath, quantizedVecBytes, capacity);
    }
}
