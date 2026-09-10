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
package com.spectrayan.spector.memory.kernel.shape;

import com.spectrayan.spector.memory.kernel.shape.Memory;
import com.spectrayan.spector.memory.kernel.layout.RegionLayout;

/**
 * Shape interface for compound hash-table storage (like co-activation tables).
 * Backs structures like CoActivationMemory.
 *
 * @param <L> the memory layout type
 */
public interface HashTableMemory<L extends RegionLayout> extends Memory<L> {
}
