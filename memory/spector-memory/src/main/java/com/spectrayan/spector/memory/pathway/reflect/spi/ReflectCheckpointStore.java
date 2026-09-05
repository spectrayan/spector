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
package com.spectrayan.spector.memory.pathway.reflect.spi;

import com.spectrayan.spector.memory.pathway.reflect.ReflectCheckpoint;

import java.util.Optional;

/**
 * Service Provider Interface for persisting and loading reflection sweep checkpoints.
 *
 * <p>Enables orchestrators to resume long sweeps from the last completed session
 * without repeating already-consolidated sessions.</p>
 *
 * @since 1.5.0
 */
public interface ReflectCheckpointStore {

    /**
     * Loads the latest checkpoint for the specified sweep identifier.
     *
     * @param sweepId unique sweep identifier
     * @return the persisted checkpoint, or empty if none exists
     */
    Optional<ReflectCheckpoint> load(String sweepId);

    /**
     * Persists or updates the checkpoint for a sweep.
     *
     * @param checkpoint the current checkpoint state
     */
    void save(ReflectCheckpoint checkpoint);

    /**
     * Deletes the checkpoint for the specified sweep.
     *
     * @param sweepId unique sweep identifier
     */
    default void delete(String sweepId) {
    }
}
