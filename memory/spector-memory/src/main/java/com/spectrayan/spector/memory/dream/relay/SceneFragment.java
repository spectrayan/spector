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
package com.spectrayan.spector.memory.dream.relay;

/**
 * Record representing an entity/role/affect decomposed fragment.
 * Biological analog: Granular representation of memory components (engram cells) 
 * distributed across cortical networks during consolidation.
 *
 * @since 1.4.0
 */
public record SceneFragment(
    String sourceMemoryId,
    int entityId,
    String entityLabel,
    FragmentRole role,
    float[] embedding,
    byte valence,
    int arousal
) {}
